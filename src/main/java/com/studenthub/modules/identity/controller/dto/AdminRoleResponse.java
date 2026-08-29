package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.RoleEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端角色选项")
public record AdminRoleResponse(
        @Schema(description = "角色 ID") Long roleId,
        @Schema(description = "角色编码") String roleCode,
        @Schema(description = "角色名称") String roleName,
        @Schema(description = "角色类型：ADMIN/REQUESTER/WORKER/DISPATCHER") String roleType,
        @Schema(description = "状态：ENABLED/DISABLED") String status) {

    public static AdminRoleResponse from(RoleEntity role) {
        return new AdminRoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(),
                role.getRoleType(), role.getStatus());
    }
}
