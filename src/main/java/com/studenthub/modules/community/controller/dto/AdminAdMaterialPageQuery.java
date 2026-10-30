package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 素材库分页查询（C13）。 */
@Schema(description = "素材库分页查询")
public record AdminAdMaterialPageQuery(
        @Schema(description = "页码") Integer pageNum,
        @Schema(description = "每页条数") Integer pageSize,
        @Schema(description = "素材名/标题关键词") String keyword,
        @Schema(description = "审核状态：PENDING/APPROVED/REJECTED") String reviewStatus,
        @Schema(description = "素材类型：IMAGE/VIDEO/TEXT") String materialType) {

    public int pageNumOrDefault() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int pageSizeOrDefault() {
        if (pageSize == null || pageSize < 1) {
            return 12;
        }
        return Math.min(pageSize, 100);
    }
}
