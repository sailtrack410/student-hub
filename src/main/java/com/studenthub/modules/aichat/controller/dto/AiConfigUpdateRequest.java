package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

@Schema(description = "AI 全局设置保存请求")
public record AiConfigUpdateRequest(
        @Schema(description = "AI 总开关") boolean enabled,
        @Schema(description = "连接超时秒") @Min(1) @Max(120) int connectTimeoutSeconds,
        @Schema(description = "读取超时秒") @Min(1) @Max(600) int readTimeoutSeconds,
        @Schema(description = "单次请求最大输入字符数") @Min(1) @Max(100000) int maxInputCharacters,
        @Schema(description = "单用户每小时消息上限") @Min(1) @Max(10000) int hourlyMessageLimit,
        @Schema(description = "携带历史消息条数") @Min(0) @Max(100) int historyWindow,
        @Schema(description = "单条用户消息最大字符数") @Min(1) @Max(100000) int maxMessageCharacters,
        // ---- 上下文策略（V54；旧客户端不传时保留原值）----
        @Schema(description = "全局上下文硬上限（tokens）", nullable = true) @Min(1000) @Max(1000000) Integer maxContextTokens,
        @Schema(description = "安全余量（tokens）", nullable = true) @Min(0) @Max(100000) Integer contextSafetyMarginTokens,
        @Schema(description = "默认最大输出（tokens）", nullable = true) @Min(64) @Max(100000) Integer maxOutputTokens,
        @Schema(description = "历史预算比例 0~1", nullable = true) @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal historyTokenRatio,
        @Schema(description = "知识库预算比例 0~1", nullable = true) @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal ragTokenRatio,
        @Schema(description = "附件预算比例 0~1", nullable = true) @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal attachmentTokenRatio,
        @Schema(description = "摘要开关", nullable = true) Boolean summaryEnabled,
        @Schema(description = "摘要触发比例 0~1", nullable = true) @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal summaryTriggerRatio,
        @Schema(description = "摘要保留最近轮数", nullable = true) @Min(1) @Max(20) Integer summaryKeepRecentTurns,
        @Schema(description = "字符/token 估算系数", nullable = true) @DecimalMin("0.5") @DecimalMax("4.0") BigDecimal tokenEstimateCharsPerToken) {
}
