package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "找回密码")
public record ResetPasswordRequest(
        @NotBlank String accountId,
        @NotBlank String email,
        @NotBlank @Size(max = 128) String newPassword,
        @NotBlank String code) {
}
