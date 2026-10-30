package com.studenthub.modules.identity.controller.dto;

import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.modules.identity.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端用户摘要")
public record AdminUserSummaryResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "手机号（管理端列表脱敏）", nullable = true) String phone,
        @Schema(description = "身份类型：STUDENT/TEACHER/LOGISTICS/ADMIN") String identityType,
        @Schema(description = "授权状态：BASIC/PENDING/AUTHORIZED/REJECTED") String authorizationStatus,
        @Schema(description = "账号状态：ENABLED/DISABLED") String status,
        @Schema(description = "社区账号类型：NORMAL/OFFICIAL_AUTO/OFFICIAL_HUMAN/AD_SERVICE") String accountType,
        @Schema(description = "是否官方账号") boolean official,
        @Schema(description = "官方头衔", nullable = true) String officialTitle,
        @Schema(description = "最近登录时间", nullable = true) LocalDateTime lastLoginAt,
        @Schema(description = "注册时间") LocalDateTime createdAt,
        @Schema(description = "已绑定角色") List<RoleItem> roles) {

    public record RoleItem(
            @Schema(description = "角色 ID") Long roleId,
            @Schema(description = "角色编码") String roleCode,
            @Schema(description = "角色名称") String roleName,
            @Schema(description = "绑定组织 ID", nullable = true) Long organizationId,
            @Schema(description = "绑定组织名称", nullable = true) String organizationName,
            @Schema(description = "数据范围") String dataScope) {
    }

    public static AdminUserSummaryResponse from(UserEntity user, List<RoleItem> roles) {
        return new AdminUserSummaryResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                PhoneMasker.mask(user.getPhone()), user.getIdentityType(), user.getAuthorizationStatus(), user.getStatus(),
                user.getAccountType() == null ? "NORMAL" : user.getAccountType(),
                Boolean.TRUE.equals(user.getOfficial()), user.getOfficialTitle(),
                user.getLastLoginAt(), user.getCreatedAt(), roles);
    }
}
