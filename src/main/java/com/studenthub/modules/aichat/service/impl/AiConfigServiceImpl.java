package com.studenthub.modules.aichat.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiProperties;
import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.integration.ai.AiRuntimeSnapshot;
import com.studenthub.modules.aichat.controller.dto.AiConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiConfigUpdateRequest;
import com.studenthub.modules.aichat.entity.AiConfigEntity;
import com.studenthub.modules.aichat.mapper.AiConfigMapper;
import com.studenthub.modules.aichat.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final Long SINGLETON_ID = 1L;
    private static final String PROVIDER_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(PROVIDER_OPENAI_COMPATIBLE);

    private final AiConfigMapper aiConfigMapper;
    private final AiProperties aiProperties;

    @Override
    public AiConfigResponse current() {
        AiConfigEntity entity = loadEntity();
        if (entity == null) {
            // DB 无配置（理论上不会发生：V16 已插入默认行），回退环境变量
            return AiConfigResponse.fromEntity(fromProperties(), aiKeyConfiguredInEnv(), "ENV");
        }
        boolean keyConfigured = StringUtils.hasText(entity.getApiKey()) || aiKeyConfiguredInEnv();
        return AiConfigResponse.fromEntity(entity, keyConfigured, "DB");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigResponse update(Long operatorId, AiConfigUpdateRequest request) {
        if (!SUPPORTED_PROVIDERS.contains(request.provider())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的 AI 供应商：" + request.provider());
        }
        AiConfigEntity entity = requireEntity();
        entity.setEnabled(request.enabled());
        entity.setProvider(request.provider());
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setModel(request.model().trim());
        entity.setConnectTimeoutSeconds(request.connectTimeoutSeconds());
        entity.setReadTimeoutSeconds(request.readTimeoutSeconds());
        entity.setMaxInputCharacters(request.maxInputCharacters());
        entity.setHourlyMessageLimit(request.hourlyMessageLimit());
        entity.setHistoryWindow(request.historyWindow());
        entity.setMaxMessageCharacters(request.maxMessageCharacters());
        // apiKey 留空 = 保持原值；填了才覆盖
        if (StringUtils.hasText(request.apiKey()) && !request.apiKey().isBlank()) {
            entity.setApiKey(request.apiKey().trim());
        }
        entity.setVersion(entity.getVersion());
        entity.setUpdatedBy(operatorId == null ? 0L : operatorId);
        aiConfigMapper.updateById(entity);

        applyRuntime(entity);
        log.info("AI 配置已更新并即时生效, operatorId={}, model={}, enabled={}",
                operatorId, entity.getModel(), entity.getEnabled());
        boolean keyConfigured = StringUtils.hasText(entity.getApiKey()) || aiKeyConfiguredInEnv();
        return AiConfigResponse.fromEntity(entity, keyConfigured, "DB");
    }

    /** 启动时刷新一次进程内快照，使 DB 配置优先于环境变量 */
    public void refreshRuntime() {
        AiConfigEntity entity = loadEntity();
        if (entity != null) {
            applyRuntime(entity);
        }
    }

    private void applyRuntime(AiConfigEntity entity) {
        String apiKey = StringUtils.hasText(entity.getApiKey())
                ? entity.getApiKey() : aiProperties.getApiKey();
        AiRuntimeConfig.update(new AiRuntimeSnapshot(
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getProvider(),
                entity.getBaseUrl(),
                apiKey,
                entity.getModel(),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getMaxInputCharacters(),
                entity.getHourlyMessageLimit(),
                entity.getHistoryWindow(),
                entity.getMaxMessageCharacters(),
                entity.getVersion() == null ? 0 : entity.getVersion()));
    }

    private AiConfigEntity fromProperties() {
        AiConfigEntity entity = new AiConfigEntity();
        entity.setId(SINGLETON_ID);
        entity.setEnabled(aiProperties.isEnabled());
        entity.setProvider(PROVIDER_OPENAI_COMPATIBLE);
        entity.setBaseUrl(aiProperties.getBaseUrl());
        entity.setApiKey(aiProperties.getApiKey());
        entity.setModel(aiProperties.getModel());
        entity.setConnectTimeoutSeconds(aiProperties.getConnectTimeoutSeconds());
        entity.setReadTimeoutSeconds(aiProperties.getReadTimeoutSeconds());
        entity.setMaxInputCharacters(aiProperties.getMaxInputCharacters());
        return entity;
    }

    private boolean aiKeyConfiguredInEnv() {
        return StringUtils.hasText(aiProperties.getApiKey());
    }

    private AiConfigEntity loadEntity() {
        return aiConfigMapper.selectById(SINGLETON_ID);
    }

    private AiConfigEntity requireEntity() {
        AiConfigEntity entity = loadEntity();
        if (entity == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 配置记录缺失，请检查 V16 迁移");
        }
        return entity;
    }
}