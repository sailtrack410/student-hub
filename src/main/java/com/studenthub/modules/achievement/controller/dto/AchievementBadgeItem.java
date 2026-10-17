package com.studenthub.modules.achievement.controller.dto;

import java.time.LocalDateTime;

/** 勋章墙单枚：全部勋章均返回（未获得也可见进度），图标本地匹配 code.png 不下发 URL */
public record AchievementBadgeItem(
        String badgeCode,
        String badgeName,
        String category,
        String rarity,
        String description,
        Integer metricThreshold,
        Integer rewardExp,
        boolean unlocked,
        LocalDateTime unlockedAt,
        Integer currentProgress) {
}
