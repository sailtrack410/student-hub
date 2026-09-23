package com.studenthub.modules.evaluation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端服务评价摘要")
public record AdminEvaluationSummaryResponse(
        Long evaluationId,
        Long requestId,
        Long workOrderId,
        Integer rating,
        String tags,
        String comment,
        String reply,
        String repliedAt,
        Long evaluatorId,
        String assigneeName,
        String createdAt
) {
}
