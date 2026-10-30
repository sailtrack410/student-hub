package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 方案B 字典热更新：客户端携带本地 catalogVersion 请求；版本一致时 changed=false 且不下发列表，
 * 节省流量；版本变化时下发完整分类与勋章字典，客户端保存本地。
 */
@Schema(description = "勋章字典（带版本）")
public record AchievementCatalogResponse(
        @Schema(description = "字典版本（16 位 hex，任何字典变更都会变化）") String catalogVersion,
        @Schema(description = "版本是否变化；false 时 categories/badges 为 null，客户端沿用本地缓存") boolean changed,
        @Schema(description = "分类字典（changed=true 时下发）", nullable = true) List<AchievementCategoryItem> categories,
        @Schema(description = "勋章字典（changed=true 时下发）", nullable = true) List<AchievementCatalogItem> badges) {
}
