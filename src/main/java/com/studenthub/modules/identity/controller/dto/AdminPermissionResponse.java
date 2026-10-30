package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.PermissionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端权限项")
public record AdminPermissionResponse(
        @Schema(description = "权限 ID") Long permissionId,
        @Schema(description = "权限编码") String permissionCode,
        @Schema(description = "权限名称") String permissionName,
        @Schema(description = "权限类型") String permissionType,
        @Schema(description = "状态") String status) {

    public static AdminPermissionResponse from(PermissionEntity p) {
        return new AdminPermissionResponse(p.getId(), p.getPermissionCode(), p.getPermissionName(),
                p.getPermissionType(), p.getStatus());
    }
}
