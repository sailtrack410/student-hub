package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "官方账号启停")
public record AdminOfficialAccountStatusRequest(
        @Schema(description = "目标状态：ENABLED/DISABLED") @NotBlank @Size(max = 16) String status) {
}
