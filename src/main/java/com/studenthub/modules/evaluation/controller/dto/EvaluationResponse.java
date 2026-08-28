package com.studenthub.modules.evaluation.controller.dto;

import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "服务评价响应")
public record EvaluationResponse(
        @Schema(description = "评价 ID") Long evaluationId,
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "评分 1-5") Integer rating,
        @Schema(description = "标签，逗号分隔", nullable = true) String tags,
        @Schema(description = "评语", nullable = true) String comment,
        @Schema(description = "被评价的服务人员", nullable = true) String assigneeName,
        @Schema(description = "评价时间") LocalDateTime createdAt) {

    public static EvaluationResponse from(ServiceEvaluationEntity entity) {
        return new EvaluationResponse(entity.getId(), entity.getRequestId(), entity.getRating(),
                entity.getTags(), entity.getComment(), entity.getAssigneeName(), entity.getCreatedAt());
    }
}
