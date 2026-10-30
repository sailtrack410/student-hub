package com.studenthub.modules.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiModelBinding;
import com.studenthub.integration.ai.AiProperties;
import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.integration.ai.AiRuntimeSnapshot;
import com.studenthub.modules.aichat.controller.dto.AiConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiConfigUpdateRequest;
import com.studenthub.modules.aichat.entity.AiConfigEntity;
import com.studenthub.modules.aichat.entity.AiDefaultModelEntity;
import com.studenthub.modules.aichat.entity.AiModelEntity;
import com.studenthub.modules.aichat.entity.AiProviderEntity;
import com.studenthub.modules.aichat.enums.AiModelType;
import com.studenthub.modules.aichat.mapper.AiConfigMapper;
import com.studenthub.modules.aichat.mapper.AiDefaultModelMapper;
import com.studenthub.modules.aichat.mapper.AiModelMapper;
import com.studenthub.modules.aichat.mapper.AiProviderMapper;
import com.studenthub.modules.aichat.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 全局设置 + 运行时快照装配。
 * 快照按「类型默认模型 → 所属启用供应商」解析连接信息；
 * DB 全部缺位时，聊天回退环境变量（AiProperties）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final Long SINGLETON_ID = 1L;

    private final AiConfigMapper aiConfigMapper;
    private final AiProviderMapper aiProviderMapper;
    private final AiModelMapper aiModelMapper;
    private final AiDefaultModelMapper aiDefaultModelMapper;
    private final AiProperties aiProperties;
    /** 快照版本号：任何重建递增，驱动 OpenAiCompatibleProvider 重建客户端 */
    private final AtomicLong versionCounter = new AtomicLong(0);

    @Override
    public AiConfigResponse current() {
        AiConfigEntity entity = requireEntity();
        AiModelBinding chat = resolveBinding(AiModelType.CHAT.name());
        return AiConfigResponse.fromEntity(entity, chat == null ? null : chat.model());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigResponse update(Long operatorId, AiConfigUpdateRequest request) {
        AiConfigEntity entity = requireEntity();
        entity.setEnabled(request.enabled());
        entity.setConnectTimeoutSeconds(request.connectTimeoutSeconds());
        entity.setReadTimeoutSeconds(request.readTimeoutSeconds());
        entity.setMaxInputCharacters(request.maxInputCharacters());
        entity.setHourlyMessageLimit(request.hourlyMessageLimit());
        entity.setHistoryWindow(request.historyWindow());
        entity.setMaxMessageCharacters(request.maxMessageCharacters());
        if (request.maxContextTokens() != null) entity.setMaxContextTokens(request.maxContextTokens());
        if (request.contextSafetyMarginTokens() != null) entity.setContextSafetyMarginTokens(request.contextSafetyMarginTokens());
        if (request.maxOutputTokens() != null) entity.setMaxOutputTokens(request.maxOutputTokens());
        if (request.historyTokenRatio() != null) entity.setHistoryTokenRatio(request.historyTokenRatio());
        if (request.ragTokenRatio() != null) entity.setRagTokenRatio(request.ragTokenRatio());
        if (request.attachmentTokenRatio() != null) entity.setAttachmentTokenRatio(request.attachmentTokenRatio());
        if (request.summaryEnabled() != null) entity.setSummaryEnabled(request.summaryEnabled());
        if (request.summaryTriggerRatio() != null) entity.setSummaryTriggerRatio(request.summaryTriggerRatio());
        if (request.summaryKeepRecentTurns() != null) entity.setSummaryKeepRecentTurns(request.summaryKeepRecentTurns());
        if (request.tokenEstimateCharsPerToken() != null) entity.setTokenEstimateCharsPerToken(request.tokenEstimateCharsPerToken());
        entity.setVersion(entity.getVersion());
        entity.setUpdatedBy(operatorId == null ? 0L : operatorId);
        aiConfigMapper.updateById(entity);

        refreshRuntime();
        log.info("AI 全局设置已更新并即时生效, operatorId={}, enabled={}", operatorId, request.enabled());
        return current();
    }

    @Override
    public void refreshRuntime() {
        AiConfigEntity config = loadEntity();
        if (config == null) {
            log.warn("ai_config 记录缺失，运行时保持环境变量默认值");
            return;
        }
        long version = versionCounter.incrementAndGet();
        AiRuntimeConfig.update(new AiRuntimeSnapshot(
                Boolean.TRUE.equals(config.getEnabled()),
                resolveBinding(AiModelType.CHAT.name()),
                resolveBinding(AiModelType.VISION.name()),
                resolveBinding(AiModelType.ASR.name()),
                resolveBinding(AiModelType.EMBEDDING.name()),
                config.getConnectTimeoutSeconds(),
                config.getReadTimeoutSeconds(),
                config.getMaxInputCharacters(),
                config.getHourlyMessageLimit(),
                config.getHistoryWindow(),
                config.getMaxMessageCharacters(),
                config.getMaxContextTokens() == null ? 60000 : config.getMaxContextTokens(),
                config.getContextSafetyMarginTokens() == null ? 512 : config.getContextSafetyMarginTokens(),
                config.getMaxOutputTokens() == null ? 2048 : config.getMaxOutputTokens(),
                config.getHistoryTokenRatio() == null ? 0.400 : config.getHistoryTokenRatio().doubleValue(),
                config.getRagTokenRatio() == null ? 0.250 : config.getRagTokenRatio().doubleValue(),
                config.getAttachmentTokenRatio() == null ? 0.150 : config.getAttachmentTokenRatio().doubleValue(),
                Boolean.TRUE.equals(config.getSummaryEnabled()),
                config.getSummaryTriggerRatio() == null ? 0.800 : config.getSummaryTriggerRatio().doubleValue(),
                config.getSummaryKeepRecentTurns() == null ? 4 : config.getSummaryKeepRecentTurns(),
                config.getTokenEstimateCharsPerToken() == null ? 1.50 : config.getTokenEstimateCharsPerToken().doubleValue(),
                (int) version));
    }

    /**
     * 按类型码解析某类型的默认模型绑定：默认模型存在 → 模型启用 → 供应商启用且已配密钥，
     * 任一环缺失即视为未配置（返回 null，调用方回退或报 AI_NOT_CONFIGURED）。
     * 语义为静默降级，与 {@link #resolveBinding(Long, String)} 显式指定型抛异常语义分开保留。
     */
    @Override
    public AiModelBinding resolveBinding(String typeCode) {
        AiDefaultModelEntity def = aiDefaultModelMapper.selectOne(new LambdaQueryWrapper<AiDefaultModelEntity>()
                .eq(AiDefaultModelEntity::getTypeCode, typeCode)
                .last("LIMIT 1"));
        if (def == null) {
            return null;
        }
        AiModelEntity model = aiModelMapper.selectById(def.getModelId());
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
            return null;
        }
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())
                || !StringUtils.hasText(provider.getApiKey())) {
            return null;
        }
        return toBinding(provider, model.getProviderModelId());
    }

    /**
     * 显式指定供应商/模型时的绑定解析（管理端流式测试）：供应商须启用且已配密钥；
     * 模型缺省取该供应商下第一个启用模型。错误显式抛出，与按类型码的静默降级语义分开。
     */
    @Override
    public AiModelBinding resolveBinding(Long providerId, String modelId) {
        AiProviderEntity provider = aiProviderMapper.selectById(providerId);
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在或未启用");
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "该供应商尚未配置 API Key");
        }
        String model = StringUtils.hasText(modelId) ? modelId : null;
        if (model == null) {
            AiModelEntity first = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                    .eq(AiModelEntity::getProviderId, providerId)
                    .eq(AiModelEntity::getEnabled, true)
                    .orderByAsc(AiModelEntity::getProviderModelId)
                    .last("LIMIT 1"));
            if (first == null) {
                throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "该供应商下没有启用的模型");
            }
            model = first.getProviderModelId();
        }
        return toBinding(provider, model);
    }

    /** 实体 → 绑定；streaming 空值兜底为支持流式（全仓该兜底唯一落点） */
    private AiModelBinding toBinding(AiProviderEntity provider, String model) {
        return new AiModelBinding(provider.getId(), provider.getBaseUrl(), provider.getApiKey(),
                model, provider.getProtocol(),
                provider.getStreaming() == null || provider.getStreaming());
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
