package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "设置本人身份请求")
public record UpdateIdentityRequest(
        @Schema(description = "身份标识，允许值 STUDENT/TEACHER") @NotBlank String identityType) {
}
