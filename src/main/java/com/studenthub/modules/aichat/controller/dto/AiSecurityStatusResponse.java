package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 安全运行时生效状态：明确哪些策略已真正接入，哪些仅配置")
public record AiSecurityStatusResponse(
        @Schema(description = "安全策略总开关") boolean policyEnabled,
        @Schema(description = "当前生效的敏感词数量") int sensitiveWordCount,
        @Schema(description = "每分钟限流（0=不限制）") int rateLimitPerMinute,
        @Schema(description = "每日配额（0=不限制）") int dailyQuotaPerUser,
        @Schema(description = "内容保留天数") int contentRetentionDays,
        @Schema(description = "是否启用历史内容自动清理") boolean retentionEnabled,
        @Schema(description = "敏感词拦截是否接入运行时") boolean sensitiveWordEnforced,
        @Schema(description = "限流/配额是否接入运行时") boolean rateLimitEnforced,
        @Schema(description = "工具白名单是否接入运行时（工具注册表未落地，恒为 false）") boolean toolWhitelistEnforced,
        @Schema(description = "提示词来源：DATABASE / CODE_FALLBACK") String promptSource,
        @Schema(description = "当前生效提示词编码与版本") String activePrompt,
        @Schema(description = "代码内置基线版本") String codeBaselineVersion,
        @Schema(description = "当前运行时已加载的提示词数量") int loadedPromptCount,
        @Schema(description = "说明") List<String> notes,
        @Schema(description = "状态生成时间") LocalDateTime generatedAt) {
}
