package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "AI 全局设置（总开关 + 超时 + 限流）；供应商与模型在供应商接口维护")
public record AiConfigResponse(
        @Schema(description = "AI 总开关") boolean enabled,
        @Schema(description = "连接超时秒") int connectTimeoutSeconds,
        @Schema(description = "读取超时秒") int readTimeoutSeconds,
        @Schema(description = "单次请求最大输入字符数") int maxInputCharacters,
        @Schema(description = "单用户每小时消息上限") int hourlyMessageLimit,
        @Schema(description = "携带历史消息条数") int historyWindow,
        @Schema(description = "单条用户消息最大字符数") int maxMessageCharacters,
        @Schema(description = "全局上下文硬上限（tokens）") int maxContextTokens,
        @Schema(description = "安全余量（tokens）") int contextSafetyMarginTokens,
        @Schema(description = "默认最大输出（tokens）") int maxOutputTokens,
        @Schema(description = "历史预算比例") BigDecimal historyTokenRatio,
        @Schema(description = "知识库预算比例") BigDecimal ragTokenRatio,
        @Schema(description = "附件预算比例") BigDecimal attachmentTokenRatio,
        @Schema(description = "摘要开关") boolean summaryEnabled,
        @Schema(description = "摘要触发比例") BigDecimal summaryTriggerRatio,
        @Schema(description = "摘要保留最近轮数") int summaryKeepRecentTurns,
        @Schema(description = "字符/token 估算系数") BigDecimal tokenEstimateCharsPerToken,
        @Schema(description = "生效的默认聊天模型，未配置为 null", nullable = true) String activeChatModel) {

    public static AiConfigResponse fromEntity(AiConfigEntity entity, String activeChatModel) {
        return new AiConfigResponse(
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getMaxInputCharacters(),
                entity.getHourlyMessageLimit(),
                entity.getHistoryWindow(),
                entity.getMaxMessageCharacters(),
                entity.getMaxContextTokens() == null ? 60000 : entity.getMaxContextTokens(),
                entity.getContextSafetyMarginTokens() == null ? 512 : entity.getContextSafetyMarginTokens(),
                entity.getMaxOutputTokens() == null ? 2048 : entity.getMaxOutputTokens(),
                entity.getHistoryTokenRatio() == null ? new BigDecimal("0.400") : entity.getHistoryTokenRatio(),
                entity.getRagTokenRatio() == null ? new BigDecimal("0.250") : entity.getRagTokenRatio(),
                entity.getAttachmentTokenRatio() == null ? new BigDecimal("0.150") : entity.getAttachmentTokenRatio(),
                Boolean.TRUE.equals(entity.getSummaryEnabled()),
                entity.getSummaryTriggerRatio() == null ? new BigDecimal("0.800") : entity.getSummaryTriggerRatio(),
                entity.getSummaryKeepRecentTurns() == null ? 4 : entity.getSummaryKeepRecentTurns(),
                entity.getTokenEstimateCharsPerToken() == null ? new BigDecimal("1.50") : entity.getTokenEstimateCharsPerToken(),
                activeChatModel);
    }
}
