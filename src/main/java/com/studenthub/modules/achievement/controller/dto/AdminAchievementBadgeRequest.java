package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端勋章/成就新建与更新请求：解锁条件 = 指标 + 阈值，满足后系统自动发放 */
@Schema(description = "勋章/成就新建与更新请求")
public record AdminAchievementBadgeRequest(
        @Schema(description = "勋章编码（创建后不可改），大写字母/数字/下划线") @Size(max = 64) String badgeCode,
        @Schema(description = "勋章名称") @NotBlank @Size(max = 64) String badgeName,
        @Schema(description = "类别：COMMUNITY/SERVICE/WORKER/JW/GROWTH") @NotBlank String category,
        @Schema(description = "稀有度：COMMON/RARE/EPIC/LEGENDARY") @NotBlank String rarity,
        @Schema(description = "解锁说明（通知文案）") @NotBlank @Size(max = 255) String description,
        @Schema(description = "解锁指标：POST_COUNT/COMMENT_COUNT/POST_LIKES/POST_LIKES_SINGLE/REQUEST_COUNT/"
                + "REQUEST_COMPLETED/EVALUATION_COUNT/EVALUATION_FIVE_STAR/WORKER_ORDER_COMPLETED/"
                + "JW_AUTH/SCHEDULE_IMPORTED/PROFILE_COMPLETE/LEVEL") @NotBlank String metricCode,
        @Schema(description = "达成阈值（≥1）") Integer metricThreshold,
        @Schema(description = "奖励经验") Integer rewardExp,
        @Schema(description = "排序（升序）") Integer sortOrder,
        @Schema(description = "是否启用（启用后参与自动解锁评估）") Boolean enabled) {
}
