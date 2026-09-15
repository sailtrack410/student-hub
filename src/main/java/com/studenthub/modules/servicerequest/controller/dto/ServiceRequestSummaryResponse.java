package com.studenthub.modules.servicerequest.controller.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "服务申请概要")
public record ServiceRequestSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "申请编号") String requestNo,
        @Schema(description = "申请标题") String title,
        @Schema(description = "服务项名称") String itemName,
        @Schema(description = "申请状态") String status,
        @Schema(description = "提交时间", nullable = true) LocalDateTime submittedAt,
        @Schema(description = "办结时间", nullable = true) LocalDateTime completedAt) {

    public static ServiceRequestSummaryResponse from(ServiceRequestEntity entity) {
        return new ServiceRequestSummaryResponse(entity.getId(), entity.getRequestNo(), entity.getTitle(),
                entity.getItemName(), entity.getStatus(), entity.getSubmittedAt(), entity.getCompletedAt());
    }
}
