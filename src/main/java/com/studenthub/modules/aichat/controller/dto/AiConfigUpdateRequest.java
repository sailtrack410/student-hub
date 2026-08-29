package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "AI 全局设置保存请求")
public record AiConfigUpdateRequest(
        @Schema(description = "AI 总开关") boolean enabled,
        @Schema(description = "连接超时秒") @Min(1) @Max(120) int connectTimeoutSeconds,
        @Schema(description = "读取超时秒") @Min(1) @Max(600) int readTimeoutSeconds,
        @Schema(description = "单次请求最大输入字符数") @Min(1) @Max(100000) int maxInputCharacters,
        @Schema(description = "单用户每小时消息上限") @Min(1) @Max(10000) int hourlyMessageLimit,
        @Schema(description = "携带历史消息条数") @Min(0) @Max(100) int historyWindow,
        @Schema(description = "单条用户消息最大字符数") @Min(1) @Max(100000) int maxMessageCharacters) {
}
