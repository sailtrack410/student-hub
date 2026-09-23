package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "管理端社区举报分页查询")
public record AdminCommunityReportPageQuery(
        @Schema(description = "页码，从 1 开始") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 50") @Min(1) @Max(50) Integer pageSize,
        @Schema(description = "按状态过滤：PENDING 待处理 / ACCEPTED 已成立 / REJECTED 已驳回；不传查全部", nullable = true) String status,
        @Schema(description = "按举报对象类型过滤：POST/COMMENT/MESSAGE/USER", nullable = true) String targetType) {

    public int pageNumOrDefault() {
        return pageNum == null ? 1 : pageNum;
    }

    public int pageSizeOrDefault() {
        return pageSize == null ? 10 : pageSize;
    }
}
