package com.studenthub.modules.servicerequest.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端请假审批详情")
public record AdminLeaveDetailResponse(
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "申请单号") String requestNo,
        @Schema(description = "标题") String title,
        @Schema(description = "事项名称") String itemName,
        @Schema(description = "申请人") AdminRequesterResponse requester,
        @Schema(description = "受理组织") Long handlerOrganizationId,
        @Schema(description = "受理组织名称") String handlerOrganizationName,
        @Schema(description = "请假类型") String leaveType,
        @Schema(description = "请假开始时间") LocalDateTime leaveStartAt,
        @Schema(description = "请假结束时间") LocalDateTime leaveEndAt,
        @Schema(description = "请假天数") BigDecimal leaveDays,
        @Schema(description = "请假事由") String leaveReason,
        @Schema(description = "申请单状态") String status,
        @Schema(description = "旧版请假状态；撤回与销假均为 CANCELLED，展示请使用 businessStatus/statusLabel") String leaveStatus,
        @Schema(description = "提交时间") LocalDateTime submittedAt,
        @Schema(description = "办结时间") LocalDateTime completedAt,
        @Schema(description = "销假时间") LocalDateTime leaveCancelAt,
        @Schema(description = "驳回原因") String rejectReason,
        @Schema(description = "撤销原因") String cancelReason,
        @Schema(description = "表单原始数据（图片已签名）") JsonNode formData,
        @Schema(description = "表单 Schema") JsonNode formSchema,
        @Schema(description = "材料图片") List<AdminRequestMaterialResponse> materials,
        @Schema(description = "处理时间线") List<RequestLogResponse> timeline,
        @Schema(description = "可执行动作：APPROVE/REJECT/CANCEL") List<String> availableActions, String approvalComment, String flowType, String itemCode,
        String businessStatus, String statusLabel) {
}
