package com.studenthub.modules.evaluation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "提交服务评价请求")
public record CreateEvaluationRequest(
        @Schema(description = "评分 1-5") @Min(1) @Max(5) Integer rating,
        @Schema(description = "评价标签，逗号分隔", nullable = true) @Size(max = 256) String tags,
        @Schema(description = "评语", nullable = true) @Size(max = 512) String comment) {
}
