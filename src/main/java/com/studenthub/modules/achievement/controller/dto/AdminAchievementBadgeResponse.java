package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 管理端勋章/成就响应：新增分类展示名与服务端图标地址（方案B字典字段） */
@Schema(description = "勋章/成就（管理端）")
public record AdminAchievementBadgeResponse(
        Long id,
        String badgeCode,
        String badgeName,
        String category,
        String categoryName,
        String rarity,
        String description,
        String metricCode,
        Integer metricThreshold,
        Integer rewardExp,
        Integer sortOrder,
        Boolean enabled,
        String iconUrl,
        LocalDateTime createdAt) {
}
