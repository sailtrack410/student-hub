package com.studenthub.modules.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiSecurityConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiSecurityConfigUpdateRequest;
import com.studenthub.modules.aichat.controller.dto.AiSecurityStatusResponse;
import com.studenthub.modules.aichat.entity.AiPromptTemplateEntity;
import com.studenthub.modules.aichat.entity.AiSecurityConfigEntity;
import com.studenthub.modules.aichat.mapper.AiPromptTemplateMapper;
import com.studenthub.modules.aichat.mapper.AiSecurityConfigMapper;
import com.studenthub.modules.aichat.security.AiPromptRuntime;
import com.studenthub.modules.aichat.security.AiSecurityRuntime;
import com.studenthub.modules.aichat.service.AiSecurityConfigService;
import com.studenthub.modules.aichat.service.CampusAssistantPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 安全策略实现：平台级单行配置（campus_id=0），保存后刷新运行时快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSecurityConfigServiceImpl implements AiSecurityConfigService {

    private static final long PLATFORM_CAMPUS_ID = 0L;

    private final AiSecurityConfigMapper configMapper;
    private final AiPromptTemplateMapper promptMapper;

    /** 上下文就绪（Flyway 迁移完成）后再加载策略，避免启动早期表不存在。 */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            refreshRuntime();
        } catch (RuntimeException exception) {
            log.warn("AI 安全策略运行时初始化失败，使用内置默认值", exception);
        }
    }

    @Override
    public AiSecurityConfigResponse current() {
        AiSecurityConfigEntity entity = findConfig();
        if (entity == null) {
            AiSecurityConfigEntity defaults = defaults();
            return AiSecurityConfigResponse.from(defaults);
        }
        return AiSecurityConfigResponse.from(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiSecurityConfigResponse update(Long operatorId, AiSecurityConfigUpdateRequest request) {
        AiSecurityConfigEntity entity = findConfig();
        if (entity == null) {
            entity = defaults();
            entity.setCampusId(PLATFORM_CAMPUS_ID);
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.sensitiveWords() != null) {
            entity.setSensitiveWords(join(request.sensitiveWords()));
        }
        if (request.toolWhitelist() != null) {
            entity.setToolWhitelist(join(request.toolWhitelist()));
        }
        if (request.rateLimitPerMinute() != null) {
            entity.setRateLimitPerMinute(request.rateLimitPerMinute());
        }
        if (request.dailyQuotaPerUser() != null) {
            entity.setDailyQuotaPerUser(request.dailyQuotaPerUser());
        }
        if (request.contentRetentionDays() != null) {
            entity.setContentRetentionDays(request.contentRetentionDays());
        }
        if (request.retentionEnabled() != null) {
            entity.setRetentionEnabled(request.retentionEnabled());
        }
        if (request.blockOnHit() != null) {
            entity.setBlockOnHit(request.blockOnHit());
        }
        entity.setRemark(request.remark());
        entity.setUpdatedBy(operatorId);
        if (entity.getId() == null) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        refreshRuntime();
        log.info("AI 安全策略已更新, operatorId={}, enabled={}, words={}",
                operatorId, entity.getEnabled(), split(entity.getSensitiveWords()).size());
        return AiSecurityConfigResponse.from(configMapper.selectById(entity.getId()));
    }

    @Override
    public AiSecurityStatusResponse status() {
        AiSecurityRuntime.Snapshot policy = AiSecurityRuntime.current();
        Long activePrompts = promptMapper.selectCount(new LambdaQueryWrapper<AiPromptTemplateEntity>()
                .eq(AiPromptTemplateEntity::getCampusId, PLATFORM_CAMPUS_ID)
                .eq(AiPromptTemplateEntity::getActive, true)
                .eq(AiPromptTemplateEntity::getStatus, "ENABLED"));
        List<String> notes = new ArrayList<>();
        notes.add("敏感词拦截已接入 AI 对话入口（命中即拒绝并返回 400），可随时关闭策略总开关");
        notes.add("每分钟限流与单用户每日配额按 ai_message 实际消息数统计，已接入运行时");
        notes.add("工具白名单当前仅持久化配置：平台尚无 AI 工具注册表，未做运行时拦截");
        if (!Boolean.TRUE.equals(policy.retentionEnabled())) {
            notes.add("内容保留天数已配置但未启用自动清理；启用后由每日任务逻辑删除过期消息");
        }
        notes.add("提示词为平台级配置（campus_id=0），未激活数据库版本时回退代码内置 "
                + CampusAssistantPrompts.PROMPT_VERSION);
        boolean databasePrompt = AiPromptRuntime.databaseBacked();
        return new AiSecurityStatusResponse(
                policy.enabled(),
                policy.sensitiveWords().size(),
                policy.rateLimitPerMinute(),
                policy.dailyQuotaPerUser(),
                policy.contentRetentionDays(),
                policy.retentionEnabled(),
                policy.enabled() && policy.blockOnHit() && !policy.sensitiveWords().isEmpty(),
                policy.enabled() && (policy.rateLimitPerMinute() > 0 || policy.dailyQuotaPerUser() > 0),
                false,
                databasePrompt ? "DATABASE" : "CODE_FALLBACK",
                databasePrompt ? AiPromptRuntime.promptVersion() : CampusAssistantPrompts.PROMPT_VERSION,
                CampusAssistantPrompts.PROMPT_VERSION,
                activePrompts == null ? 0 : activePrompts.intValue(),
                notes,
                LocalDateTime.now());
    }

    @Override
    public void refreshRuntime() {
        AiSecurityConfigEntity entity = findConfig();
        if (entity == null) {
            AiSecurityRuntime.reset();
            return;
        }
        AiSecurityRuntime.update(new AiSecurityRuntime.Snapshot(
                !Boolean.FALSE.equals(entity.getEnabled()),
                split(entity.getSensitiveWords()),
                split(entity.getToolWhitelist()),
                nonNegative(entity.getRateLimitPerMinute(), 30),
                nonNegative(entity.getDailyQuotaPerUser(), 200),
                nonNegative(entity.getContentRetentionDays(), 90),
                Boolean.TRUE.equals(entity.getRetentionEnabled()),
                !Boolean.FALSE.equals(entity.getBlockOnHit())));
    }

    private AiSecurityConfigEntity findConfig() {
        return configMapper.selectOne(new LambdaQueryWrapper<AiSecurityConfigEntity>()
                .eq(AiSecurityConfigEntity::getCampusId, PLATFORM_CAMPUS_ID)
                .last("LIMIT 1"));
    }

    private AiSecurityConfigEntity defaults() {
        AiSecurityConfigEntity entity = new AiSecurityConfigEntity();
        entity.setCampusId(PLATFORM_CAMPUS_ID);
        entity.setEnabled(true);
        entity.setSensitiveWords("");
        entity.setToolWhitelist("AI_SEARCH_CAMPUS,CALENDAR_QUERY,SERVICE_GUIDE,REPAIR_CREATE");
        entity.setRateLimitPerMinute(30);
        entity.setDailyQuotaPerUser(200);
        entity.setContentRetentionDays(90);
        entity.setRetentionEnabled(false);
        entity.setBlockOnHit(true);
        return entity;
    }

    private int nonNegative(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, value);
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList());
    }

    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split("[,\\n]")).stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
