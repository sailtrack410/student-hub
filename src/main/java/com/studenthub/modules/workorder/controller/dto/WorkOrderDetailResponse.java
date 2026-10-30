package com.studenthub.modules.workorder.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.servicerequest.controller.dto.RequestLogResponse;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.service.WorkOrderSla;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

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
        @Schema(description = "处理人联系电话（师傅电话，一键拨打用）", nullable = true) String assigneePhone,
        @Schema(description = "申请人", nullable = true) String requesterName,
        @Schema(description = "报修人联系方式", nullable = true) String requesterPhone,
        @Schema(description = "报修地址", nullable = true) String repairAddress,
        @Schema(description = "申请人填写的表单内容", nullable = true) JsonNode formData,
        @Schema(description = "表单字段定义", nullable = true) JsonNode formSchema,
        @Schema(description = "是否需要拍照确认") boolean photoWorkflowRequired,
        @Schema(description = "现场照片地址", nullable = true) String arrivalPhotoUrl,
        @Schema(description = "完工照片地址", nullable = true) String completionPhotoUrl,
        @Schema(description = "全部完工照片地址", nullable = true) List<String> completionPhotoUrls,
        @Schema(description = "完工提交时间", nullable = true) LocalDateTime completionSubmittedAt,
        @Schema(description = "报修人确认时间", nullable = true) LocalDateTime confirmedAt,
        @Schema(description = "确认退回原因", nullable = true) String confirmationRejectReason,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "派单时间", nullable = true) LocalDateTime assignedAt,
        @Schema(description = "开始时间", nullable = true) LocalDateTime startedAt,
        @Schema(description = "完成时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "完成说明", nullable = true) String completeRemark,
        @Schema(description = "SLA 承诺时限（小时）") Integer slaHours,
        @Schema(description = "SLA 截止时间", nullable = true) LocalDateTime dueAt,
        @Schema(description = "SLA 剩余小时数（负数表示已超时；终态为空）", nullable = true) Double remainingHours,
        @Schema(description = "是否已超时") boolean overdue,
        @Schema(description = "是否即将超时（4 小时内）") boolean dueSoon,
        @Schema(description = "已超时小时数", nullable = true) Double overdueHours,
        @Schema(description = "处理时间线（申请全生命周期日志）", nullable = true) List<RequestLogResponse> timeline) {

    /**
     * 静态工厂：附属查询结果（requesterName/requesterPhone/formData/repairAddress/照片签名地址等）
     * 由 service 组装后传入，DTO 只负责装配字段，避免 service 手拼构造器。
     */
    public static WorkOrderDetailResponse from(WorkOrderEntity order, String requestNo,
                                               String requesterName, String requesterPhone,
                                               String assigneePhone,
                                               JsonNode formData, JsonNode formSchema,
                                               String repairAddress,
                                               String arrivalPhotoUrl, String completionPhotoUrl,
                                               List<String> completionPhotoUrls,
                                               List<RequestLogResponse> timeline) {
        LocalDateTime now = LocalDateTime.now();
        Double remaining = WorkOrderSla.remainingHours(order, now);
        return new WorkOrderDetailResponse(order.getId(), order.getOrderNo(), order.getRequestId(), requestNo,
                order.getTitle(), order.getStatus(), order.getPriority(), order.getAssigneeName(), assigneePhone,
                requesterName, requesterPhone, repairAddress, formData, formSchema,
                Boolean.TRUE.equals(order.getPhotoWorkflowRequired()), arrivalPhotoUrl, completionPhotoUrl,
                completionPhotoUrls == null ? List.of() : completionPhotoUrls,
                order.getCompletionSubmittedAt(), order.getConfirmedAt(), order.getConfirmationRejectReason(),
                order.getCreatedAt(), order.getAssignedAt(), order.getStartedAt(), order.getCompletedAt(),
                order.getCompleteRemark(),
                WorkOrderSla.effectiveSlaHours(order), WorkOrderSla.effectiveDueAt(order),
                remaining == null ? null : WorkOrderSla.round1(remaining),
                WorkOrderSla.overdue(order, now), WorkOrderSla.dueSoon(order, now),
                WorkOrderSla.overdueHours(order, now),
                timeline == null ? List.of() : timeline);
    }
}
