package com.studenthub.modules.achievement.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 成就档案（打开即评估后的结果视图） */
public record AchievementProfileResponse(
        int level,
        String levelName,
        int totalExp,
        Integer nextLevel,
        String nextLevelName,
        Integer nextLevelExp,
        Integer expToNext,
        int progressPercent,
        long badgeCount,
        long totalBadgeCount,
        String wornBadgeCode,
        List<RecentBadge> recentBadges) {

    public record RecentBadge(String badgeCode, String badgeName, LocalDateTime obtainedAt) {
    }
}
