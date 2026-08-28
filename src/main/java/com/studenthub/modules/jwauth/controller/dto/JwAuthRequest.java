package com.studenthub.modules.jwauth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "教务认证请求")
public record JwAuthRequest(
        @Schema(description = "SSO 统一身份账号（学号/工号）") @NotBlank String ssoAccount,
        @Schema(description = "统一身份密码，仅当次使用不存储") @NotBlank String password,
        @Schema(description = "验证码 uid，与获取验证码时一致") @NotBlank String captchaUid,
        @Schema(description = "算术题验证码答案") @NotBlank String captchaCode) {
}
