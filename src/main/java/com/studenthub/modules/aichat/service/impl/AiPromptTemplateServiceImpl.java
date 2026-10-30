package com.studenthub.modules.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiPromptTemplateRequest;
import com.studenthub.modules.aichat.controller.dto.AiPromptTemplateResponse;
import com.studenthub.modules.aichat.entity.AiPromptTemplateEntity;
import com.studenthub.modules.aichat.mapper.AiPromptTemplateMapper;
import com.studenthub.modules.aichat.security.AiPromptRuntime;
import com.studenthub.modules.aichat.service.AiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 提示词版本管理实现。
 *
 * <p>约束：已激活版本内容不可原地修改（保证审计可回溯）；激活新版本会自动让同编码旧版本失活；
 * 停用后运行时立即回退到代码内置基线，不会出现「提示词为空」的裸跑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl implements AiPromptTemplateService {

    /** 提示词为平台级配置，不随管理端校区切换（运行时面向全校区）。 */
    private static final long PLATFORM_CAMPUS_ID = 0L;
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");

    private final AiPromptTemplateMapper promptMapper;

    /** 上下文就绪（Flyway 迁移完成）后再加载数据库版本，避免启动早期表不存在。 */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            refreshRuntime();
        } catch (RuntimeException exception) {
            log.warn("AI 提示词运行时初始化失败，将回退代码内置基线", exception);
        }
    }

    @Override
    public List<AiPromptTemplateResponse> list(String keyword, String scene, String status) {
        String normalizedStatus = normalizeEnum(status);
        LambdaQueryWrapper<AiPromptTemplateEntity> wrapper = new LambdaQueryWrapper<AiPromptTemplateEntity>()
                .eq(AiPromptTemplateEntity::getCampusId, PLATFORM_CAMPUS_ID)
                .eq(StringUtils.hasText(normalizedStatus), AiPromptTemplateEntity::getStatus, normalizedStatus)
                .eq(StringUtils.hasText(scene), AiPromptTemplateEntity::getScene, normalizeEnum(scene))
                .and(StringUtils.hasText(keyword), w -> {
                    String value = keyword.trim();
                    w.like(AiPromptTemplateEntity::getPromptCode, value)
                            .or().like(AiPromptTemplateEntity::getPromptName, value);
                })
                .orderByAsc(AiPromptTemplateEntity::getPromptCode)
                .orderByDesc(AiPromptTemplateEntity::getVersionNo);
        return promptMapper.selectList(wrapper).stream().map(AiPromptTemplateResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateResponse create(AiPromptTemplateRequest request) {
        String code = request.promptCode().trim().toUpperCase();
        Integer maxVersion = promptMapper.selectList(new LambdaQueryWrapper<AiPromptTemplateEntity>()
                        .select(AiPromptTemplateEntity::getVersionNo)
                        .eq(AiPromptTemplateEntity::getCampusId, PLATFORM_CAMPUS_ID)
                        .eq(AiPromptTemplateEntity::getPromptCode, code))
                .stream().map(AiPromptTemplateEntity::getVersionNo)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0);
        AiPromptTemplateEntity entity = new AiPromptTemplateEntity();
        entity.setCampusId(PLATFORM_CAMPUS_ID);
        entity.setPromptCode(code);
        entity.setVersionNo(maxVersion + 1);
        entity.setActive(false);
        entity.setStatus("ENABLED");
        applyRequest(entity, request);
        promptMapper.insert(entity);
        log.info("AI 提示词版本已创建, code={}, version={}", code, entity.getVersionNo());
        return AiPromptTemplateResponse.from(promptMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateResponse update(Long id, AiPromptTemplateRequest request) {
        AiPromptTemplateEntity entity = requirePrompt(id);
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "已生效版本不可原地修改，请新建版本后激活");
        }
        String code = request.promptCode() == null ? null : request.promptCode().trim().toUpperCase();
        if (StringUtils.hasText(code) && !code.equals(entity.getPromptCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "提示词编码创建后不可修改");
        }
        applyRequest(entity, request);
        promptMapper.updateById(entity);
        return AiPromptTemplateResponse.from(promptMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateResponse activate(Long id) {
        AiPromptTemplateEntity entity = requirePrompt(id);
        if (!"ENABLED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "已停用版本不可激活，请先启用或新建版本");
        }
        // 同编码其他版本失活（含跨校区，平台级）
        List<AiPromptTemplateEntity> siblings = promptMapper.selectList(
                new LambdaQueryWrapper<AiPromptTemplateEntity>()
                        .eq(AiPromptTemplateEntity::getCampusId, PLATFORM_CAMPUS_ID)
                        .eq(AiPromptTemplateEntity::getPromptCode, entity.getPromptCode())
                        .eq(AiPromptTemplateEntity::getActive, true));
        for (AiPromptTemplateEntity sibling : siblings) {
            if (sibling.getId().equals(entity.getId())) {
                continue;
            }
            sibling.setActive(false);
            promptMapper.updateById(sibling);
        }
        entity.setActive(true);
        promptMapper.updateById(entity);
        refreshRuntime();
        log.info("AI 提示词版本已激活, code={}, version={}", entity.getPromptCode(), entity.getVersionNo());
        return AiPromptTemplateResponse.from(promptMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateResponse disable(Long id) {
        AiPromptTemplateEntity entity = requirePrompt(id);
        boolean wasActive = Boolean.TRUE.equals(entity.getActive());
        entity.setStatus("DISABLED");
        if (wasActive) {
            entity.setActive(false);
        }
        promptMapper.updateById(entity);
        if (wasActive) {
            refreshRuntime();
        }
        return AiPromptTemplateResponse.from(promptMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiPromptTemplateEntity entity = requirePrompt(id);
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "当前生效版本不可删除，请先停用或切换版本");
        }
        promptMapper.deleteById(entity.getId());
        log.info("AI 提示词版本已删除, code={}, version={}", entity.getPromptCode(), entity.getVersionNo());
    }

    @Override
    public void refreshRuntime() {
        AiPromptTemplateEntity active = promptMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplateEntity>()
                .eq(AiPromptTemplateEntity::getCampusId, PLATFORM_CAMPUS_ID)
                .eq(AiPromptTemplateEntity::getActive, true)
                .eq(AiPromptTemplateEntity::getStatus, "ENABLED")
                .orderByDesc(AiPromptTemplateEntity::getVersionNo)
                .last("LIMIT 1"));
        if (active == null) {
            AiPromptRuntime.clear();
            return;
        }
        AiPromptRuntime.update(new AiPromptRuntime.Snapshot(
                active.getPromptCode(), active.getVersionNo(), active.getSystemPrompt()));
    }

    private void applyRequest(AiPromptTemplateEntity entity, AiPromptTemplateRequest request) {
        entity.setPromptName(request.promptName().trim());
        entity.setScene(normalizeEnum(request.scene()));
        entity.setSystemPrompt(request.systemPrompt());
        entity.setUserPromptTemplate(request.userPromptTemplate());
        entity.setVariables(request.variables() == null || request.variables().isEmpty()
                ? null : String.join(",", request.variables().stream().filter(StringUtils::hasText)
                .map(String::trim).distinct().toList()));
        entity.setChangeNote(request.changeNote());
        entity.setRemark(request.remark());
    }

    private AiPromptTemplateEntity requirePrompt(Long id) {
        AiPromptTemplateEntity entity = id == null ? null : promptMapper.selectById(id);
        if (entity == null || !Long.valueOf(PLATFORM_CAMPUS_ID).equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提示词版本不存在");
        }
        return entity;
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
