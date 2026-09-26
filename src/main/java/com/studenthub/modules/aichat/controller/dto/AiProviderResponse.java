package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiProviderEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端 AI 供应商")
public record AiProviderResponse(
        @Schema(description = "供应商 ID") Long providerId,
        @Schema(description = "供应商编码（唯一）") String providerCode,
        @Schema(description = "显示名称") String providerName,
        @Schema(description = "供应商图标标识（lobe-icons 名）", nullable = true) String icon,
        @Schema(description = "服务地址（兼容 OpenAI 协议，可含 /v1）") String baseUrl,
        @Schema(description = "调用协议：OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES") String protocol,
        @Schema(description = "支持流式输出（SSE）") boolean streaming,
        @Schema(description = "API Key 是否已配置") boolean apiKeyConfigured,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "上次连通性检查时间", nullable = true) LocalDateTime lastCheckAt,
        @Schema(description = "上次检查是否通过", nullable = true) Boolean lastCheckOk,
        @Schema(description = "上次检查结果说明", nullable = true) String lastCheckResult,
        @Schema(description = "已添加模型数") long modelCount) {

    public static AiProviderResponse from(AiProviderEntity entity, long modelCount) {
        return new AiProviderResponse(entity.getId(), entity.getProviderCode(), entity.getProviderName(),
                entity.getIcon(),
                entity.getBaseUrl(),
                entity.getProtocol() == null ? "OPENAI_CHAT" : entity.getProtocol(),
                // streaming 空值 = 默认开启流式（等价 null→true 兜底；绑定构造的唯一兜底落点在 AiConfigServiceImpl）
                !Boolean.FALSE.equals(entity.getStreaming()),
                entity.getApiKey() != null && !entity.getApiKey().isBlank(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getLastCheckAt(), entity.getLastCheckOk(), entity.getLastCheckResult(),
                modelCount);
    }
}
