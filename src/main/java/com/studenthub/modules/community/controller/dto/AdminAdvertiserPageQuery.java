package com.studenthub.modules.community.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端社区广告主分页查询")
public record AdminAdvertiserPageQuery(
        @Schema(description = "页码，从 1 开始") Integer pageNum,
        @Schema(description = "每页条数，最大 50") Integer pageSize,
        @Schema(description = "关键词：名称/联系人/联系电话") String keyword,
        @Schema(description = "状态：ENABLED/DISABLED") String status) implements PageQuery {
}
