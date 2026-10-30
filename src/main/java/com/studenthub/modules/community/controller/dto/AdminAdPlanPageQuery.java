package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 投放计划分页查询（C13）。 */
@Schema(description = "投放计划分页查询")
public record AdminAdPlanPageQuery(
        @Schema(description = "页码，从 1 开始") Integer pageNum,
        @Schema(description = "每页条数，最大 100") Integer pageSize,
        @Schema(description = "计划名关键词") String keyword,
        @Schema(description = "状态：DRAFT/ENABLED/DISABLED") String status,
        @Schema(description = "广告主 ID") Long advertiserId,
        @Schema(description = "广告位：FEED/BANNER/SPLASH/SERVICE_HALL") String slot) {

    public int pageNumOrDefault() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int pageSizeOrDefault() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
