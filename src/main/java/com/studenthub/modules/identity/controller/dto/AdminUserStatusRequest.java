package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "管理端启用/停用账号请求")
public record AdminUserStatusRequest(
        @Schema(description = "目标状态：ENABLED 启用 / DISABLED 停用", allowableValues = {"ENABLED", "DISABLED"})
        @NotBlank String status) {
}
