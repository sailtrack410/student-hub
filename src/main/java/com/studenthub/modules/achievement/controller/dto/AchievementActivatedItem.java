package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 用户态-已激活（已解锁）勋章 */
@Schema(description = "已激活勋章")
public record AchievementActivatedItem(
        @Schema(description = "勋章编码") String badgeCode,
        @Schema(description = "解锁时间") LocalDateTime unlockedAt) {
}
