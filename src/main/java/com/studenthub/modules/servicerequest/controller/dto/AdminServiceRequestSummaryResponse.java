package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端服务申请摘要")
public record AdminServiceRequestSummaryResponse(
        Long requestId,
        String requestNo,
        String itemName,
        String title,
        Long requesterId,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        String rejectReason
) {
}
