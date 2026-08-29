package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 配置响应（API Key 脱敏）")
public record AiConfigResponse(
        @Schema(description = "是否启用 AI") boolean enabled,
        @Schema(description = "供应商标识") String provider,
        @Schema(description = "AI 服务地址") String baseUrl,
        @Schema(description = "API Key 是否已配置") boolean apiKeyConfigured,
        @Schema(description = "模型名") String model,
        @Schema(description = "连接超时秒") int connectTimeoutSeconds,
        @Schema(description = "读取超时秒") int readTimeoutSeconds,
        @Schema(description = "单次请求最大输入字符数") int maxInputCharacters,
        @Schema(description = "单用户每小时消息上限") int hourlyMessageLimit,
        @Schema(description = "携带历史消息条数") int historyWindow,
        @Schema(description = "单条用户消息最大字符数") int maxMessageCharacters,
        @Schema(description = "配置来源：DB 或 ENV") String source) {

    public static AiConfigResponse fromEntity(AiConfigEntity entity, boolean apiKeyConfigured, String source) {
        return new AiConfigResponse(
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getProvider(),
                entity.getBaseUrl(),
                apiKeyConfigured,
                entity.getModel(),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getMaxInputCharacters(),
                entity.getHourlyMessageLimit(),
                entity.getHistoryWindow(),
                entity.getMaxMessageCharacters(),
                source);
    }
}