package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "更新个人资料请求")
public record UpdateProfileRequest(
        @Schema(description = "昵称") @NotBlank @Size(max = 32) String displayName) {
}
