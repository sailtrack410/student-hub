package com.studenthub.modules.workorder.controller.dto;

import com.studenthub.common.persistence.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "工单分页查询")
public record WorkOrderPageQuery(
        @Schema(description = "页码，从 1 开始") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 50") @Min(1) @Max(50) Integer pageSize,
        @Schema(description = "按工单状态过滤，可选值 PENDING_DISPATCH/ASSIGNED/PROCESSING/COMPLETED/REJECTED/CANCELLED；PROCESSING 包含拍照与待确认节点",
                nullable = true) String status) implements PageQuery {
}
