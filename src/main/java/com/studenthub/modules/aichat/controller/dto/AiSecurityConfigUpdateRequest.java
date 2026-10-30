package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "AI 安全策略更新")
public record AiSecurityConfigUpdateRequest(
        @Schema(description = "策略总开关") Boolean enabled,
        @Schema(description = "敏感词列表，命中即拦截（blockOnHit=true 时）") List<@Size(max = 64) String> sensitiveWords,
        @Schema(description = "工具白名单（当前仅配置与展示）") List<@Size(max = 64) String> toolWhitelist,
        @Schema(description = "每分钟每用户最大消息数，0 不限制") @Min(0) @Max(600) Integer rateLimitPerMinute,
        @Schema(description = "每用户每日最大消息数，0 不限制") @Min(0) @Max(10000) Integer dailyQuotaPerUser,
        @Schema(description = "内容保留天数（retentionEnabled=true 时生效）") @Min(0) @Max(3650) Integer contentRetentionDays,
        @Schema(description = "是否启用历史内容自动清理（默认关闭）") Boolean retentionEnabled,
        @Schema(description = "命中敏感词是否直接拦截") Boolean blockOnHit,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
