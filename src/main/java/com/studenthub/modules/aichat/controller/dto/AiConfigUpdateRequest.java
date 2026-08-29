package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "AI 配置管理请求")
public record AiConfigUpdateRequest(
        @Schema(description = "是否启用 AI") boolean enabled,
        @Schema(description = "供应商标识") @NotBlank @Size(max = 32) String provider,
        @Schema(description = "AI 服务地址") @NotBlank @Size(max = 255) String baseUrl,
        @Schema(description = "API Key（留空表示保持原值）", nullable = true) @Size(max = 512) String apiKey,
        @Schema(description = "模型名") @NotBlank @Size(max = 128) String model,
        @Schema(description = "连接超时秒") @Min(1) @Max(120) int connectTimeoutSeconds,
        @Schema(description = "读取超时秒") @Min(1) @Max(600) int readTimeoutSeconds,
        @Schema(description = "单次请求最大输入字符数") @Min(1) @Max(100000) int maxInputCharacters,
        @Schema(description = "单用户每小时消息上限") @Min(1) @Max(10000) int hourlyMessageLimit,
        @Schema(description = "携带历史消息条数") @Min(0) @Max(100) int historyWindow,
        @Schema(description = "单条用户消息最大字符数") @Min(1) @Max(100000) int maxMessageCharacters) {

    public static AiConfigUpdateRequest fromEntity(AiConfigEntity entity) {
        return new AiConfigUpdateRequest(
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getProvider(),
                entity.getBaseUrl(),
                null,
                entity.getModel(),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getMaxInputCharacters(),
                entity.getHourlyMessageLimit(),
                entity.getHistoryWindow(),
                entity.getMaxMessageCharacters());
    }
}