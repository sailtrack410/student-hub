package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * /auth/me 成长扩展段（API需求清单 二-1）：等级经验实时重算，uid 取教务学号（未认证 null）。
 * 独立 record 而非并入 AuthenticatedUser——认证主体不带业务派生数据。
 */
@Schema(description = "当前用户成长与佩戴信息")
public record GrowthSummary(
        @Schema(description = "展示学号/工号（教务认证后才有，未认证 null）", nullable = true) String uid,
        @Schema(description = "当前等级 1-9") int level,
        @Schema(description = "等级名称") String levelName,
        @Schema(description = "当前累计经验") int currentExp,
        @Schema(description = "下一级所需累计经验（满级为 null）", nullable = true) Integer nextLevelExp,
        @Schema(description = "距下一级还差经验（满级为 null）", nullable = true) Integer expToNext,
        @Schema(description = "佩戴勋章编码，未佩戴 null", nullable = true) String wornBadgeCode) {
}
