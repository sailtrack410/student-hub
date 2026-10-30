package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "角色更新请求（编码不可修改）")
public record AdminRoleUpdateRequest(
        @Schema(description = "角色名称") @NotBlank @Size(max = 128) String roleName,
        @Schema(description = "角色类型") @NotBlank @Size(max = 32) String roleType,
        @Schema(description = "状态 ENABLED/DISABLED") @NotBlank String status) {
}
