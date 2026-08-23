package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "开发环境密码登录请求")
public record PasswordLoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        @Schema(description = "开发环境账号用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(max = 128, message = "密码长度不能超过128个字符")
        @Schema(description = "开发环境账号密码", example = "DevOnly123!", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        String password) {
}
