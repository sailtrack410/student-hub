package com.studenthub.modules.servicerequest.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端服务申请详情：申请人、表单内容、材料附件、工单、评价与时间线")
public record AdminServiceRequestDetailResponse(
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "申请编号") String requestNo,
        @Schema(description = "申请标题") String title,
        @Schema(description = "申请状态") String status,
        @Schema(description = "服务项 ID") Long itemId,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "服务项图标", nullable = true) String itemIcon,
        @Schema(description = "服务分类 ID") Long categoryId,
        @Schema(description = "服务分类名称", nullable = true) String categoryName,
        @Schema(description = "承诺办结时限（小时）", nullable = true) Integer slaHours,
        @Schema(description = "受理组织 ID", nullable = true) Long handlerOrganizationId,
        @Schema(description = "受理组织名称", nullable = true) String handlerOrganizationName,
        @Schema(description = "申请人信息") AdminRequesterResponse requester,
        @Schema(description = "提交时间", nullable = true) LocalDateTime submittedAt,
        @Schema(description = "办结时间", nullable = true) LocalDateTime completedAt,
        @Schema(description = "驳回/退回原因", nullable = true) String rejectReason,
        @Schema(description = "撤销原因", nullable = true) String cancelReason,
        @Schema(description = "申请人填写的表单内容（图片已签名）", nullable = true) JsonNode formData,
        @Schema(description = "表单字段定义", nullable = true) JsonNode formSchema,
        @Schema(description = "图片/材料字段（图片已签名）") List<AdminRequestMaterialResponse> materials,
        @Schema(description = "工单照片附件（已签名）") List<AdminRequestAttachmentResponse> attachments,
        @Schema(description = "关联工单", nullable = true) WorkOrderSummaryResponse workOrder,
        @Schema(description = "服务评价", nullable = true) AdminRequestEvaluationResponse evaluation,
        @Schema(description = "状态时间线") List<RequestLogResponse> timeline,
        @Schema(description = "当前可执行动作：APPROVE / REJECT / COMPLETE / LEAVE_CANCEL；维修为空") List<String> availableActions, String flowType, String itemCode, String businessStatus, String statusLabel,
        String approvalComment) {
}
