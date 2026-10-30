package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 字典-勋章分类（分类名与排序随字典下发） */
@Schema(description = "勋章分类")
public record AchievementCategoryItem(
        @Schema(description = "分类编码") String categoryCode,
        @Schema(description = "分类展示名") String categoryName,
        @Schema(description = "排序（升序）") Integer sortOrder) {
}
