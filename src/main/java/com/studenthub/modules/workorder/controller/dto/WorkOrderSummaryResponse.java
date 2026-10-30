package com.studenthub.modules.workorder.controller.dto;

import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.service.WorkOrderSla;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "工单概要")
public record WorkOrderSummaryResponse(
        @Schema(description = "工单 ID") Long orderId,
        @Schema(description = "工单号") String orderNo,
        @Schema(description = "关联申请 ID") Long requestId,
        @Schema(description = "工单标题") String title,
        @Schema(description = "工单状态") String status,
        @Schema(description = "优先级") String priority,
        @Schema(description = "当前处理人", nullable = true) String assigneeName,
        @Schema(description = "处理人联系电话（师傅电话）", nullable = true) String assigneePhone,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "派单时间", nullable = true) LocalDateTime assignedAt,
        @Schema(description = "完成时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "完成说明", nullable = true) String completeRemark,
        @Schema(description = "是否需要拍照确认") boolean photoWorkflowRequired,
        @Schema(description = "完工确认退回原因", nullable = true) String confirmationRejectReason,
        @Schema(description = "现场照片地址", nullable = true) String arrivalPhotoUrl,
        @Schema(description = "完工照片地址", nullable = true) String completionPhotoUrl,
        @Schema(description = "全部完工照片地址", nullable = true) List<String> completionPhotoUrls,
        @Schema(description = "SLA 承诺时限（小时）") Integer slaHours,
        @Schema(description = "SLA 截止时间", nullable = true) LocalDateTime dueAt,
        @Schema(description = "SLA 剩余小时数（负数表示已超时；终态为空）", nullable = true) Double remainingHours,
        @Schema(description = "是否已超时") boolean overdue,
        @Schema(description = "是否即将超时（4 小时内）") boolean dueSoon,
        @Schema(description = "已超时小时数", nullable = true) Double overdueHours) {

    public static WorkOrderSummaryResponse from(WorkOrderEntity entity) {
        return from(entity, null, null, List.of());
    }

    public static WorkOrderSummaryResponse from(WorkOrderEntity entity, String arrivalPhotoUrl,
                                                String completionPhotoUrl) {
        return from(entity, arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrl == null
                ? List.of() : List.of(completionPhotoUrl));
    }

    public static WorkOrderSummaryResponse from(WorkOrderEntity entity, String arrivalPhotoUrl,
                                                String completionPhotoUrl, List<String> completionPhotoUrls) {
        return from(entity, null, arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrls);
    }

    public static WorkOrderSummaryResponse from(WorkOrderEntity entity, String assigneePhone, String arrivalPhotoUrl,
                                                String completionPhotoUrl, List<String> completionPhotoUrls) {
        LocalDateTime now = LocalDateTime.now();
        Double remaining = WorkOrderSla.remainingHours(entity, now);
        return new WorkOrderSummaryResponse(entity.getId(), entity.getOrderNo(), entity.getRequestId(),
                entity.getTitle(), entity.getStatus(), entity.getPriority(), entity.getAssigneeName(), assigneePhone,
                entity.getCreatedAt(), entity.getAssignedAt(), entity.getCompletedAt(), entity.getCompleteRemark(),
                Boolean.TRUE.equals(entity.getPhotoWorkflowRequired()), entity.getConfirmationRejectReason(),
                arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrls == null ? List.of() : completionPhotoUrls,
                WorkOrderSla.effectiveSlaHours(entity), WorkOrderSla.effectiveDueAt(entity),
                remaining == null ? null : WorkOrderSla.round1(remaining),
                WorkOrderSla.overdue(entity, now), WorkOrderSla.dueSoon(entity, now),
                WorkOrderSla.overdueHours(entity, now));
    }
}
