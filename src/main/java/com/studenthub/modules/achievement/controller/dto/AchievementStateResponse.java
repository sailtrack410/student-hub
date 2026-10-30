package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 方案B 高频轻量接口：每次启动/进入成长页拉取，只下发「已激活 + 当前佩戴 + 进度 + 字典版本」，
 * 客户端用 catalogVersion 判断是否需要重新拉字典。
 */
@Schema(description = "徽章用户态：激活/佩戴/进度")
public record AchievementStateResponse(
        @Schema(description = "当前字典版本，与 catalog 一致") String catalogVersion,
        @Schema(description = "当前佩戴勋章编码；未佩戴为 null", nullable = true) String wornBadgeCode,
        @Schema(description = "已激活（已解锁）勋章，按字典顺序") List<AchievementActivatedItem> activated,
        @Schema(description = "全部启用勋章的当前进度，按字典顺序") List<AchievementProgressItem> progress) {
}
