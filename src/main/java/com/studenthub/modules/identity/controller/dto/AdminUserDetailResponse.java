package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户详情（U01）：资料、脱敏手机号、组织、身份、账号状态、角色绑定、官方账号、最近登录。
 *
 * <p>手机号固定走 {@code PhoneMasker} 脱敏，不返回密码哈希、登录凭证与第三方 openId。
 * 数据范围与组织绑定来自 sys_user_role，供管理员核对授权来源。</p>
 */
@Schema(description = "管理端用户详情")
public record AdminUserDetailResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "手机号（已脱敏）", nullable = true) String phone,
        @Schema(description = "邮箱", nullable = true) String email,
        @Schema(description = "学号", nullable = true) String studentNo,
        @Schema(description = "院系", nullable = true) String college,
        @Schema(description = "性别", nullable = true) String gender,
        @Schema(description = "头像地址", nullable = true) String avatarUrl,
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "校区名称", nullable = true) String campusName,
        @Schema(description = "默认组织 ID", nullable = true) Long defaultOrganizationId,
        @Schema(description = "默认组织名称", nullable = true) String defaultOrganizationName,
        @Schema(description = "身份类型") String identityType,
        @Schema(description = "授权状态") String authorizationStatus,
        @Schema(description = "账号状态") String status,
        @Schema(description = "社区账号类型") String accountType,
        @Schema(description = "是否官方账号") boolean official,
        @Schema(description = "官方头衔", nullable = true) String officialTitle,
        @Schema(description = "官方号运营人", nullable = true) String accountOperator,
        @Schema(description = "官方号简介", nullable = true) String accountDescription,
        @Schema(description = "认证状态") String verificationStatus,
        @Schema(description = "最近登录时间", nullable = true) LocalDateTime lastLoginAt,
        @Schema(description = "注册时间") LocalDateTime createdAt,
        @Schema(description = "角色绑定") List<RoleBinding> roles,
        @Schema(description = "已登记登录设备数") int deviceCount) {

    @Schema(description = "用户角色绑定")
    public record RoleBinding(
            @Schema(description = "角色 ID") Long roleId,
            @Schema(description = "角色编码") String roleCode,
            @Schema(description = "角色名称") String roleName,
            @Schema(description = "绑定组织 ID", nullable = true) Long organizationId,
            @Schema(description = "绑定组织名称", nullable = true) String organizationName,
            @Schema(description = "数据范围") String dataScope) {
    }
}
