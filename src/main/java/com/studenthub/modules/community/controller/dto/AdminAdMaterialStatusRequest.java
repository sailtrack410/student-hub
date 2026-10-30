package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 素材启停请求（C13）：ENABLED/DISABLED。 */
@Schema(description = "素材状态请求")
public record AdminAdMaterialStatusRequest(
        @Schema(description = "目标状态：ENABLED/DISABLED") @NotBlank String status) {
}
