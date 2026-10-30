package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 用户态-单枚勋章当前进度（未解锁也返回，用于墙上的 x/threshold 进度条） */
@Schema(description = "勋章进度")
public record AchievementProgressItem(
        @Schema(description = "勋章编码") String badgeCode,
        @Schema(description = "当前指标值") int currentProgress) {
}
