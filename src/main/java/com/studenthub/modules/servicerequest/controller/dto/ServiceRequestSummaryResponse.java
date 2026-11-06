package com.studenthub.modules.servicerequest.controller.dto;

import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.service.RequestBusinessView;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "服务申请概要")
public record ServiceRequestSummaryResponse(
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "申请编号") String requestNo,
        @Schema(description = "申请标题") String title,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "申请状态") String status,
        @Schema(description = "提交时间", nullable = true) LocalDateTime submittedAt,
        @Schema(description = "办结时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "关联工单 ID", nullable = true) Long workOrderId,
        @Schema(description = "关联工单号", nullable = true) String workOrderNo,
        @Schema(description = "关联工单状态", nullable = true) String workOrderStatus,
        @Schema(description = "当前处理人", nullable = true) String assigneeName,
        String flowType, String itemCode, String businessStatus, String statusLabel, java.util.List<String> availableActions,
        String leaveType, LocalDateTime leaveStartAt, LocalDateTime leaveEndAt, java.math.BigDecimal leaveDays,
        String approvalComment, LocalDateTime leaveCancelAt) {

    public static ServiceRequestSummaryResponse from(ServiceRequestEntity entity) {
        return from(entity, null);
    }

    public static ServiceRequestSummaryResponse from(ServiceRequestEntity entity, WorkOrderEntity order) {
        return from(entity, order, false);
    }

    public static ServiceRequestSummaryResponse from(ServiceRequestEntity entity, WorkOrderEntity order, boolean evaluated) {
        if (!RequestBusinessView.repair(entity) || order != null && Boolean.TRUE.equals(order.getFlowArchived())) order = null;
        return new ServiceRequestSummaryResponse(entity.getId(), entity.getRequestNo(), entity.getTitle(),
                entity.getItemName(), entity.getStatus(), entity.getSubmittedAt(), entity.getCompletedAt(),
                order == null ? null : order.getId(),
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getStatus(),
                order == null ? null : order.getAssigneeName(),
                RequestBusinessView.flowType(entity), entity.getItemCode(),
                RequestBusinessView.status(entity, order == null ? null : order.getStatus()),
                RequestBusinessView.label(entity, order == null ? null : order.getStatus()),
                RequestBusinessView.actions(entity, order == null ? null : order.getStatus(), evaluated),
                entity.getLeaveType(), entity.getLeaveStartAt(), entity.getLeaveEndAt(), entity.getLeaveDays(),
                entity.getApprovalComment(), entity.getLeaveCancelAt());
    }
}
