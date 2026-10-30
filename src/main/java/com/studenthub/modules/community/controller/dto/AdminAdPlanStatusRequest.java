package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 投放计划启停（C13）：ENABLED / DISABLED / DRAFT。 */
@Schema(description = "投放计划状态请求")
public record AdminAdPlanStatusRequest(
        @Schema(description = "目标状态：ENABLED/DISABLED/DRAFT") @NotBlank String status) {
}
