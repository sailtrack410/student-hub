package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端分区新建/更新请求 */
@Schema(description = "社区分区新建/更新请求")
public record AdminCommunitySectionRequest(
        @Schema(description = "分区编码（创建后不可改），如 LOST_FOUND") @Size(max = 32) String sectionCode,
        @Schema(description = "分区名称") @NotBlank @Size(max = 64) String sectionName,
        @Schema(description = "图标标识（客户端素材名）") @Size(max = 128) String icon,
        @Schema(description = "分区简介") @Size(max = 255) String description,
        @Schema(description = "横幅标题") @Size(max = 128) String bannerTitle,
        @Schema(description = "横幅副标题") @Size(max = 255) String bannerSubtitle,
        @Schema(description = "横幅图片地址") @Size(max = 255) String bannerImage,
        @Schema(description = "排序（升序）") Integer sortOrder,
        @Schema(description = "推荐权重（数值越大越靠前，0 表示不额外推荐）") Integer recommendWeight,
        @Schema(description = "状态：ENABLED/DISABLED") String status) {
}
