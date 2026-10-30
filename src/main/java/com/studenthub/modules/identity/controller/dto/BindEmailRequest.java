package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "绑定校园邮箱")
public record BindEmailRequest(@NotBlank @Email String email, @NotBlank String code) {
}
