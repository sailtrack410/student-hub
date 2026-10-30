package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        @Schema(description = "请假状态 PENDING/APPROVED/REJECTED/CANCELLED") String leaveStatus,
        @Schema(description = "提交时间") LocalDateTime submittedAt,
        @Schema(description = "办结时间") LocalDateTime completedAt,
        @Schema(description = "销假时间") LocalDateTime leaveCancelAt,
        @Schema(description = "驳回原因") String rejectReason) {
}
