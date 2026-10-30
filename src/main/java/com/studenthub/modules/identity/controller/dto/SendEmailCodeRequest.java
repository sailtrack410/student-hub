package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "发送校园邮箱验证码")
public record SendEmailCodeRequest(
        @Schema(description = "接收验证码的邮箱，可自定义")
        @NotBlank @Email String email,
        @Schema(description = "用途：REGISTER / RESET / BIND") @NotBlank String purpose) {
}
