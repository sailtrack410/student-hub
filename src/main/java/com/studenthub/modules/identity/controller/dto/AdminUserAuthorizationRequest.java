package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理端授权审核请求（U04：支持拒绝原因模板 + 自定义补充）")
public record AdminUserAuthorizationRequest(
        @Schema(description = "审核动作：APPROVE 通过 / REJECT 拒绝", allowableValues = {"APPROVE", "REJECT"})
        @NotBlank String action,
        @Schema(description = "拒绝原因模板编码；REJECT 时与 reason 至少提供一个",
                nullable = true) @Size(max = 64) String reasonCode,
        @Schema(description = "审核备注：通过时作为备注；拒绝时作为模板文案的补充说明", nullable = true)
        @Size(max = 200) String reason) {
}
