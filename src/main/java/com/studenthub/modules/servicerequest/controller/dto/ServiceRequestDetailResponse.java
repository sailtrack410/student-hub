package com.studenthub.modules.servicerequest.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.service.RequestBusinessView;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "服务申请详情")
public record ServiceRequestDetailResponse(
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "申请编号") String requestNo,
        @Schema(description = "申请标题") String title,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "服务项图标", nullable = true) String itemIcon,
        @Schema(description = "申请状态") String status,
        @Schema(description = "承诺办结时限（小时）", nullable = true) Integer slaHours,
        @Schema(description = "表单内容", nullable = true) JsonNode formData,
        @Schema(description = "驳回原因", nullable = true) String rejectReason,
        @Schema(description = "撤销原因", nullable = true) String cancelReason,
        @Schema(description = "提交时间", nullable = true) LocalDateTime submittedAt,
        @Schema(description = "办结时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "工单信息", nullable = true) WorkOrderSummaryResponse workOrder,
        @Schema(description = "评价信息", nullable = true) EvaluationResponse evaluation,
        @Schema(description = "状态时间线") List<RequestLogResponse> timeline,
        String flowType, String itemCode, String businessStatus, String statusLabel, List<String> availableActions,
        String leaveType, LocalDateTime leaveStartAt, LocalDateTime leaveEndAt, java.math.BigDecimal leaveDays,
        String approvalComment, LocalDateTime leaveCancelAt) {

    public static ServiceRequestDetailResponse from(ServiceRequestEntity entity, String itemIcon,
                                                    Integer slaHours, JsonNode formData,
                                                    WorkOrderSummaryResponse workOrder,
                                                    EvaluationResponse evaluation,
                                                    List<RequestLogResponse> timeline) {
        return new ServiceRequestDetailResponse(entity.getId(), entity.getRequestNo(), entity.getTitle(),
                entity.getItemName(), itemIcon, entity.getStatus(), slaHours, formData,
                entity.getRejectReason(), entity.getCancelReason(), entity.getSubmittedAt(), entity.getCompletedAt(),
                workOrder, evaluation, timeline,
                RequestBusinessView.flowType(entity), entity.getItemCode(),
                RequestBusinessView.status(entity, workOrder == null ? null : workOrder.status()),
                RequestBusinessView.label(entity, workOrder == null ? null : workOrder.status()),
                RequestBusinessView.actions(entity, workOrder == null ? null : workOrder.status(), evaluation != null),
                entity.getLeaveType(), entity.getLeaveStartAt(), entity.getLeaveEndAt(), entity.getLeaveDays(),
                entity.getApprovalComment(), entity.getLeaveCancelAt());
    }
}
