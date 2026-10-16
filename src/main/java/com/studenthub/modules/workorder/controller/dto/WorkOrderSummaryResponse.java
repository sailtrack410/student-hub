package com.studenthub.modules.workorder.controller.dto;

import com.studenthub.modules.workorder.entity.WorkOrderEntity;
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
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "派单时间", nullable = true) LocalDateTime assignedAt,
        @Schema(description = "完成时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "完成说明", nullable = true) String completeRemark,
        @Schema(description = "是否需要拍照确认") boolean photoWorkflowRequired,
        @Schema(description = "完工确认退回原因", nullable = true) String confirmationRejectReason,
        @Schema(description = "现场照片地址", nullable = true) String arrivalPhotoUrl,
        @Schema(description = "完工照片地址", nullable = true) String completionPhotoUrl,
        @Schema(description = "全部完工照片地址", nullable = true) List<String> completionPhotoUrls) {

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
        return new WorkOrderSummaryResponse(entity.getId(), entity.getOrderNo(), entity.getRequestId(),
                entity.getTitle(), entity.getStatus(), entity.getPriority(), entity.getAssigneeName(),
                entity.getCreatedAt(), entity.getAssignedAt(), entity.getCompletedAt(), entity.getCompleteRemark(),
                Boolean.TRUE.equals(entity.getPhotoWorkflowRequired()), entity.getConfirmationRejectReason(),
                arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrls == null ? List.of() : completionPhotoUrls);
    }
}
