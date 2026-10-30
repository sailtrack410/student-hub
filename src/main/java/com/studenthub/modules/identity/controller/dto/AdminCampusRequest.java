package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCampusRequest(
        @Schema(description = "校区编码，大写字母/数字/下划线") @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]{2,32}$", message = "校区编码仅支持 2-32 位大写字母、数字或下划线")
        String campusCode,
        @Schema(description = "校区名称") @NotBlank @Size(max = 128) String campusName,
        @Schema(description = "状态：ENABLED/DISABLED，默认 ENABLED") String status) {
}
