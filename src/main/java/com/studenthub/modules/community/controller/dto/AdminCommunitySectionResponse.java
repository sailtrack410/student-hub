package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 管理端分区响应（含推荐权重，V71） */
@Schema(description = "社区分区（管理端）")
public record AdminCommunitySectionResponse(
        Long sectionId,
        String sectionCode,
        String sectionName,
        String icon,
        String description,
        String bannerTitle,
        String bannerSubtitle,
        String bannerImage,
        Integer sortOrder,
        String status,
        long postCount,
        @Schema(description = "推荐权重：数值越大越靠前") Integer recommendWeight) {
}
