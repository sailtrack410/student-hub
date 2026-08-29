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

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_VISION = "VISION";

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
        AiModelBinding chat = resolveBinding(TYPE_CHAT);
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
                resolveBinding(TYPE_CHAT),
                resolveBinding(TYPE_VISION),
                config.getConnectTimeoutSeconds(),
                config.getReadTimeoutSeconds(),
                config.getMaxInputCharacters(),
                config.getHourlyMessageLimit(),
                config.getHistoryWindow(),
                config.getMaxMessageCharacters(),
                (int) version));
    }

    /**
     * 解析某类型的默认模型绑定：默认模型存在 → 模型启用 → 供应商启用且已配密钥，
     * 任一环缺失即视为未配置（返回 null，调用方回退或报 AI_NOT_CONFIGURED）。
     */
    private AiModelBinding resolveBinding(String typeCode) {
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
        return new AiModelBinding(provider.getId(), provider.getBaseUrl(), provider.getApiKey(),
                model.getProviderModelId());
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
