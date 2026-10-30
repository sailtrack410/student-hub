package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "管理端角色详情")
public record AdminRoleDetailResponse(
        @Schema(description = "角色 ID") Long roleId,
        @Schema(description = "角色编码") String roleCode,
        @Schema(description = "角色名称") String roleName,
        @Schema(description = "角色类型") String roleType,
        @Schema(description = "状态") String status,
        @Schema(description = "已绑定权限编码") List<String> permissionCodes,
        @Schema(description = "已绑定权限 ID") List<Long> permissionIds) {
}
