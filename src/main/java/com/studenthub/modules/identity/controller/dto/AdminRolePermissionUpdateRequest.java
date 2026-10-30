package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "角色权限整体覆盖请求")
public record AdminRolePermissionUpdateRequest(
        @Schema(description = "权限 ID 全量列表") @NotNull List<Long> permissionIds) {
}
