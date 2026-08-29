package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端用户摘要")
public record AdminUserSummaryResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "手机号", nullable = true) String phone,
        @Schema(description = "身份类型：STUDENT/TEACHER/LOGISTICS/ADMIN") String identityType,
        @Schema(description = "授权状态：BASIC/PENDING/AUTHORIZED/REJECTED") String authorizationStatus,
        @Schema(description = "账号状态：ENABLED/DISABLED") String status,
        @Schema(description = "最近登录时间", nullable = true) LocalDateTime lastLoginAt,
        @Schema(description = "注册时间") LocalDateTime createdAt,
        @Schema(description = "已绑定角色") List<RoleItem> roles) {

    public record RoleItem(
            @Schema(description = "角色 ID") Long roleId,
            @Schema(description = "角色编码") String roleCode,
            @Schema(description = "角色名称") String roleName) {
    }

    public static AdminUserSummaryResponse from(UserEntity user, List<RoleItem> roles) {
        return new AdminUserSummaryResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getPhone(), user.getIdentityType(), user.getAuthorizationStatus(), user.getStatus(),
                user.getLastLoginAt(), user.getCreatedAt(), roles);
    }
}
