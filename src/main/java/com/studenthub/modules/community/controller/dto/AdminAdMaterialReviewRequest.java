package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 素材审核请求（C13）：APPROVE 通过 / REJECT 驳回（必须填写原因）。 */
@Schema(description = "素材审核请求")
public record AdminAdMaterialReviewRequest(
        @Schema(description = "审核结论：APPROVE/REJECT") @NotBlank String action,
        @Schema(description = "驳回原因（REJECT 必填）") @Size(max = 255) String reason) {
}
