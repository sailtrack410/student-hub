package com.studenthub.modules.servicerequest.controller.dto;

import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端申请详情-服务评价")
public record AdminRequestEvaluationResponse(
        @Schema(description = "评价 ID") Long evaluationId,
        @Schema(description = "评分 1-5") Integer rating,
        @Schema(description = "标签", nullable = true) String tags,
        @Schema(description = "评语", nullable = true) String comment,
        @Schema(description = "被评价服务人员", nullable = true) String assigneeName,
        @Schema(description = "商家回复", nullable = true) String reply,
        @Schema(description = "回复时间", nullable = true) LocalDateTime repliedAt,
        @Schema(description = "评价时间") LocalDateTime createdAt) {

    public static AdminRequestEvaluationResponse from(ServiceEvaluationEntity entity) {
        return new AdminRequestEvaluationResponse(entity.getId(), entity.getRating(), entity.getTags(),
                entity.getComment(), entity.getAssigneeName(), entity.getReply(), entity.getRepliedAt(),
                entity.getCreatedAt());
    }
}
