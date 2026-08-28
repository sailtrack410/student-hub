package com.studenthub.modules.servicerequest.controller.dto;

import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "申请状态时间线条目")
public record RequestLogResponse(
        @Schema(description = "动作：SUBMIT/DISPATCH/START/COMPLETE/REJECT/CANCEL/EVALUATE") String action,
        @Schema(description = "操作人") String operatorName,
        @Schema(description = "说明", nullable = true) String comment,
        @Schema(description = "操作时间") LocalDateTime createdAt) {

    public static RequestLogResponse from(ServiceRequestLogEntity entity) {
        return new RequestLogResponse(entity.getAction(), entity.getOperatorName(), entity.getComment(),
                entity.getCreatedAt());
    }
}
