package com.studenthub.modules.push.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.push.controller.dto.PushTemplateRequest;
import com.studenthub.modules.push.controller.dto.PushTemplateResponse;
import com.studenthub.modules.push.controller.dto.PushTemplateTestRequest;
import com.studenthub.modules.push.controller.dto.PushTemplateTestResponse;
import com.studenthub.modules.push.entity.PushTemplateEntity;
import com.studenthub.modules.push.mapper.PushTemplateMapper;
import com.studenthub.modules.push.provider.PushChannels;
import com.studenthub.modules.push.provider.PushDeliveryResult;
import com.studenthub.modules.push.provider.PushDeliveryStatus;
import com.studenthub.modules.push.provider.PushMessage;
import com.studenthub.modules.push.provider.PushProvider;
import com.studenthub.modules.push.provider.PushProviderRegistry;
import com.studenthub.modules.push.service.PushTemplateRenderer;
import com.studenthub.modules.push.service.PushTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推送模板实现：按管理端校区上下文隔离；模板本身不发送，测试发送仅发往当前管理员。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushTemplateServiceImpl implements PushTemplateService {

    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");
    private static final int NOTIFICATION_CONTENT_LIMIT = 500;
    private static final int NOTIFICATION_TITLE_LIMIT = 120;

    private final PushTemplateMapper templateMapper;
    private final PushProviderRegistry providerRegistry;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    @Override
    public List<PushTemplateResponse> list(String keyword, String scene, String status) {
        String normalizedStatus = normalizeEnum(status);
        LambdaQueryWrapper<PushTemplateEntity> wrapper = new LambdaQueryWrapper<PushTemplateEntity>()
                .eq(PushTemplateEntity::getCampusId, campusId())
                .eq(StringUtils.hasText(normalizedStatus), PushTemplateEntity::getStatus, normalizedStatus)
                .eq(StringUtils.hasText(scene), PushTemplateEntity::getScene, normalizeEnum(scene))
                .and(StringUtils.hasText(keyword), w -> {
                    String value = keyword.trim();
                    w.like(PushTemplateEntity::getTemplateName, value)
                            .or().like(PushTemplateEntity::getTemplateCode, value)
                            .or().like(PushTemplateEntity::getTitleTemplate, value);
                })
                .orderByDesc(PushTemplateEntity::getUpdatedAt);
        return templateMapper.selectList(wrapper).stream().map(PushTemplateResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PushTemplateResponse create(PushTemplateRequest request) {
        Long campusId = campusId();
        String code = request.templateCode().trim().toUpperCase();
        Long exists = templateMapper.selectCount(new LambdaQueryWrapper<PushTemplateEntity>()
                .eq(PushTemplateEntity::getCampusId, campusId)
                .eq(PushTemplateEntity::getTemplateCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板编码已存在：" + code);
        }
        PushTemplateEntity entity = new PushTemplateEntity();
        entity.setCampusId(campusId);
        entity.setTemplateCode(code);
        applyRequest(entity, request);
        templateMapper.insert(entity);
        log.info("推送模板已创建, campusId={}, code={}", campusId, code);
        return PushTemplateResponse.from(templateMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PushTemplateResponse update(Long id, PushTemplateRequest request) {
        PushTemplateEntity entity = requireTemplate(id);
        String code = request.templateCode() == null ? null : request.templateCode().trim().toUpperCase();
        if (StringUtils.hasText(code) && !code.equals(entity.getTemplateCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板编码创建后不可修改");
        }
        applyRequest(entity, request);
        templateMapper.updateById(entity);
        return PushTemplateResponse.from(templateMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PushTemplateEntity entity = requireTemplate(id);
        templateMapper.deleteById(entity.getId());
        log.info("推送模板已删除, id={}, code={}", entity.getId(), entity.getTemplateCode());
    }

    @Override
    public PushTemplateTestResponse test(Long id, PushTemplateTestRequest request) {
        PushTemplateEntity template = requireTemplate(id);
        Map<String, String> variables = request == null || request.variables() == null
                ? Map.of() : request.variables();
        String titleSource = StringUtils.hasText(request == null ? null : request.titleOverride())
                ? request.titleOverride() : template.getTitleTemplate();
        String contentSource = StringUtils.hasText(request == null ? null : request.contentOverride())
                ? request.contentOverride() : template.getContentTemplate();

        PushTemplateRenderer.RenderResult titleRendered = PushTemplateRenderer.render(titleSource, variables);
        PushTemplateRenderer.RenderResult contentRendered = PushTemplateRenderer.render(contentSource, variables);
        Set<String> missing = new LinkedHashSet<>(titleRendered.missing());
        missing.addAll(contentRendered.missing());
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "模板变量缺失：" + String.join(", ", missing));
        }

        List<String> channels = request != null && request.channels() != null && !request.channels().isEmpty()
                ? parseChannels(request.channels())
                : PushChannels.parse(template.getDefaultChannels());
        if (channels.isEmpty()) {
            channels = List.of(PushChannels.LOG);
        }

        AuthenticatedUser admin = currentUserProvider.requireCurrentUser();
        // 测试发送必达路径：写入当前管理员的站内通知（真实落库，App 消息中心可见）。
        notificationService.send(new NotificationSendCommand(
                admin.userId(),
                "PUSH_TEST",
                truncate(titleRendered.text(), NOTIFICATION_TITLE_LIMIT),
                truncate("【推送模板测试】" + contentRendered.text(), NOTIFICATION_CONTENT_LIMIT),
                "PUSH_TEMPLATE",
                template.getId()));

        List<PushTemplateTestResponse.ChannelOutcome> results = new ArrayList<>();
        List<PushTemplateTestResponse.ChannelOutcome> notConfigured = new ArrayList<>();
        List<String> configuredChannels = new ArrayList<>();
        for (String channel : channels) {
            PushProvider provider = providerRegistry.resolve(channel);
            if (provider == null || !provider.configured()) {
                String reason = provider == null ? "未知渠道" : provider.unconfiguredReason();
                PushTemplateTestResponse.ChannelOutcome outcome =
                        new PushTemplateTestResponse.ChannelOutcome(channel, PushDeliveryStatus.NOT_CONFIGURED, reason);
                results.add(outcome);
                notConfigured.add(outcome);
                continue;
            }
            configuredChannels.add(channel);
            PushDeliveryResult result = providerRegistry.send(channel, new PushMessage(
                    null, admin.userId(), titleRendered.text(), contentRendered.text(),
                    Map.of("notificationType", "PUSH_TEST", "templateCode", template.getTemplateCode())));
            results.add(new PushTemplateTestResponse.ChannelOutcome(channel, result.status(), result.message()));
        }

        return new PushTemplateTestResponse(true, titleRendered.text(), contentRendered.text(),
                channels, configuredChannels, notConfigured, results);
    }

    private void applyRequest(PushTemplateEntity entity, PushTemplateRequest request) {
        String status = normalizeEnum(request.status());
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板状态仅支持 ENABLED/DISABLED");
        }
        entity.setTemplateName(request.templateName().trim());
        entity.setScene(normalizeEnum(request.scene()));
        entity.setTitleTemplate(request.titleTemplate());
        entity.setContentTemplate(request.contentTemplate());
        List<String> channels = request.defaultChannels() == null || request.defaultChannels().isEmpty()
                ? List.of(PushChannels.LOG) : parseChannels(request.defaultChannels());
        entity.setDefaultChannels(String.join(",", channels));
        entity.setVariables(toVariablesJson(request.variables(), request.titleTemplate(), request.contentTemplate()));
        entity.setStatus(status == null ? "ENABLED" : status);
        entity.setRemark(request.remark());
    }

    private List<String> parseChannels(List<String> channels) {
        String raw = String.join(",", channels);
        try {
            List<String> parsed = PushChannels.parseAndValidate(raw);
            if (parsed.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "至少选择一个推送渠道");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, exception.getMessage());
        }
    }

    private String toVariablesJson(List<String> declared, String title, String content) {
        List<String> variables = declared == null || declared.isEmpty()
                ? PushTemplateRenderer.variables(
                        (title == null ? "" : title) + (content == null ? "" : content))
                : declared.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (variables.isEmpty()) {
            return null;
        }
        return variables.stream().map(v -> "\"" + v.replace("\"", "") + "\"")
                .reduce((a, b) -> a + "," + b).map(json -> "[" + json + "]").orElse(null);
    }

    private PushTemplateEntity requireTemplate(Long id) {
        PushTemplateEntity entity = id == null ? null : templateMapper.selectById(id);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "推送模板不存在");
        }
        return entity;
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }
}
