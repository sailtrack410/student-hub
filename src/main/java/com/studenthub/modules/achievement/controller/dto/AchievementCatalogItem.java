package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字典-单枚勋章（不含用户态）：内置 19 枚 iconUrl 为 null，客户端按本地 assets 匹配；
 * 管理端新增码上传图标后返回带版本参数的远程地址。
 */
@Schema(description = "勋章字典项")
public record AchievementCatalogItem(
        @Schema(description = "勋章编码") String badgeCode,
        @Schema(description = "勋章名称") String badgeName,
        @Schema(description = "分类编码") String categoryCode,
        @Schema(description = "稀有度：COMMON/RARE/EPIC/LEGENDARY") String rarity,
        @Schema(description = "解锁说明") String description,
        @Schema(description = "达成阈值") Integer metricThreshold,
        @Schema(description = "奖励经验") Integer rewardExp,
        @Schema(description = "排序（升序）") Integer sortOrder,
        @Schema(description = "服务端图标地址；null 表示客户端用内置本地素材", nullable = true) String iconUrl) {
}
