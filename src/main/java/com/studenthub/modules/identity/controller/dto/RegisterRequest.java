package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "账号注册：登录 ID + 密码，邮箱验证码确认")
public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 20) String accountId,
        @NotBlank String email,
        @NotBlank @Size(max = 128) String password,
        @NotBlank String code) {
}
