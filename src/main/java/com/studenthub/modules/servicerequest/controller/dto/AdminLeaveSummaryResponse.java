package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端请假申请摘要")
public record AdminLeaveSummaryResponse(
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "申请单号") String requestNo,
        @Schema(description = "申请人 ID") Long requesterId,
        @Schema(description = "申请人姓名") String requesterName,
        @Schema(description = "申请人组织") String requesterOrganizationName,
        @Schema(description = "请假类型") String leaveType,
        @Schema(description = "请假开始时间") LocalDateTime leaveStartAt,
        @Schema(description = "请假结束时间") LocalDateTime leaveEndAt,
        @Schema(description = "请假天数") BigDecimal leaveDays,
        @Schema(description = "申请单状态 SUBMITTED/PROCESSING/COMPLETED/REJECTED/CANCELLED") String status,
        @Schema(description = "旧版请假状态 PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED；撤回与销假均为 CANCELLED，展示请使用 businessStatus/statusLabel") String leaveStatus,
        @Schema(description = "提交时间") LocalDateTime submittedAt,
        @Schema(description = "办结时间") LocalDateTime completedAt,
        @Schema(description = "销假时间") LocalDateTime leaveCancelAt,
        @Schema(description = "驳回原因") String rejectReason,
        @Schema(description = "申请时的流程快照") String flowType,
        @Schema(description = "申请时的事项代码") String itemCode,
        @Schema(description = "业务状态：PENDING_APPROVAL/APPROVED/REJECTED/CANCELLED（已撤回）/CLOSED（已销假）/COMPLETED（历史已办结）") String businessStatus,
        @Schema(description = "业务状态名称") String statusLabel,
        @Schema(description = "管理端可执行动作：APPROVE/REJECT/CANCEL（销假）") List<String> availableActions) {
}
