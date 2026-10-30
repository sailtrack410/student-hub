package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 发放记录（SYS09）：grantSource=AUTO 自动达成 / MANUAL 管理端手动发放。 */
@Schema(description = "成就发放记录")
public record AdminAchievementGrantResponse(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户昵称") String userName,
        @Schema(description = "勋章编码") String badgeCode,
        @Schema(description = "勋章名称") String badgeName,
        @Schema(description = "当前进度（自动达成时用于展示）") Integer currentProgress,
        @Schema(description = "解锁时间（空表示未达成）") LocalDateTime unlockedAt,
        @Schema(description = "发放来源：AUTO/MANUAL") String grantSource) {
}
