package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 手动发放勋章请求（SYS09）：用于活动奖励、补偿等运营场景。 */
@Schema(description = "手动发放勋章请求")
public record AdminAchievementGrantRequest(
        @Schema(description = "目标用户 ID") @NotNull Long userId,
        @Schema(description = "勋章编码，如 COMMUNITY_POST_10") @NotBlank @Size(max = 64) String badgeCode,
        @Schema(description = "发放备注，写入审计日志", nullable = true) @Size(max = 255) String note) {
}
