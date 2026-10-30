package com.studenthub.modules.community.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端官方直投广告分页查询")
public record AdminDirectAdPageQuery(
        @Schema(description = "页码，从 1 开始") Integer pageNum,
        @Schema(description = "每页条数，最大 50") Integer pageSize,
        @Schema(description = "配置状态：DRAFT/ENABLED/DISABLED") String status,
        @Schema(description = "审核状态：DRAFT/PENDING/APPROVED/REJECTED") String reviewStatus,
        @Schema(description = "广告主 ID") Long advertiserId,
        @Schema(description = "广告位：FEED/BANNER/SPLASH/SERVICE_HALL") String slot,
        @Schema(description = "关键词：标题/品牌") String keyword) implements PageQuery {
}
