package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "设置登录账号与密码")
public record UpdateAccountCredentialsRequest(
        @Schema(description = "登录账号（4-20 位字母/数字/下划线）", example = "student_2024")
        @NotBlank @Size(min = 4, max = 20) String username,
        @Schema(description = "新密码（长度与复杂度由管理端安全策略决定）", example = "Campus2026")
        @NotBlank @Size(max = 128) String password,
        @Schema(description = "当前密码；已设置密码时必填", nullable = true)
        String currentPassword) {
}
