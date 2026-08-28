package com.studenthub.modules.workorder.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "工单详情（含关联申请信息）")
public record WorkOrderDetailResponse(
        @Schema(description = "工单 ID") Long orderId,
        @Schema(description = "工单号") String orderNo,
        @Schema(description = "关联申请 ID") Long requestId,
        @Schema(description = "申请编号", nullable = true) String requestNo,
        @Schema(description = "工单标题") String title,
        @Schema(description = "工单状态") String status,
        @Schema(description = "优先级") String priority,
        @Schema(description = "当前处理人", nullable = true) String assigneeName,
        @Schema(description = "申请人", nullable = true) String requesterName,
        @Schema(description = "申请人填写的表单内容", nullable = true) JsonNode formData,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "派单时间", nullable = true) LocalDateTime assignedAt,
        @Schema(description = "开始时间", nullable = true) LocalDateTime startedAt,
        @Schema(description = "完成时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "完成说明", nullable = true) String completeRemark) {
}
