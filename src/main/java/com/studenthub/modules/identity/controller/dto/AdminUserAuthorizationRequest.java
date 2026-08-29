package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端授权审核请求")
public record AdminUserAuthorizationRequest(
        @Schema(description = "审核动作：APPROVE 通过 / REJECT 拒绝", allowableValues = {"APPROVE", "REJECT"})
        @NotBlank String action,
        @Schema(description = "审核备注，拒绝时建议填写", nullable = true) @Size(max = 200) String reason) {
}
