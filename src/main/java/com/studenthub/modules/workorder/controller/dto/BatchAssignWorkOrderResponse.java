package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "批量派单结果（逐条返回，失败不影响其他工单）")
public record BatchAssignWorkOrderResponse(
        @Schema(description = "请求工单数") int requested,
        @Schema(description = "派发成功数") int success,
        @Schema(description = "失败明细") List<Failure> failures) {

    @Schema(description = "单条派发失败原因")
    public record Failure(
            @Schema(description = "工单 ID") Long orderId,
            @Schema(description = "失败原因") String message) {
    }
}
