package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端社区广告主启停")
public record AdminAdvertiserStatusRequest(
        @Schema(description = "目标状态：ENABLED/DISABLED")
        @NotBlank @Size(max = 16) String status) {
}
