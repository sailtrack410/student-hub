package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.RoleScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "当前登录用户信息")
public record CurrentUserResponse(
        @Schema(description = "用户 ID", example = "1001")
        Long userId,
        @Schema(description = "所属校区 ID", example = "1")
        Long campusId,
        @Schema(description = "默认组织 ID", example = "10")
        Long defaultOrganizationId,
        @Schema(description = "平台用户名", example = "wechat_user_abc123")
        String username,
        @Schema(description = "用户展示名称", example = "微信用户")
        String displayName,
        @Schema(description = "固定身份类型：STUDENT 学生、TEACHER 教师、LOGISTICS 后勤、ADMIN 管理员",
                example = "STUDENT", allowableValues = {"STUDENT", "TEACHER", "LOGISTICS", "ADMIN"})
        String identityType,
        @Schema(description = "身份授权状态：BASIC 基础权限、PENDING 待审核、AUTHORIZED 已授权、REJECTED 已拒绝",
                example = "BASIC", allowableValues = {"BASIC", "PENDING", "AUTHORIZED", "REJECTED"})
        String authorizationStatus,
        @Schema(description = "已生效的角色编码", example = "[REQUESTER]")
        List<String> roles,
        @Schema(description = "角色及其数据范围")
        List<RoleScope> roleScopes,
        @Schema(description = "已生效的权限编码", example = "[service-request:create]")
        List<String> permissions,
        @Schema(description = "头像文件访问路径（未签名，展示时需换取签名地址）", nullable = true)
        String avatarPath,
        @Schema(description = "联系电话", nullable = true, example = "15700000000")
        String phone,
        @Schema(description = "成长与佩戴信息（等级/经验实时重算）", nullable = true)
        GrowthSummary growth) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.userId(),
                user.campusId(),
                user.defaultOrganizationId(),
                user.username(),
                user.displayName(),
                user.identityType(),
                user.authorizationStatus(),
                user.roleCodes(),
                user.roleScopes(),
                user.permissionCodes(),
                user.avatarFileId() == null ? null : "/api/common/files/view/" + user.avatarFileId(),
                user.phone(),
                null);
    }
}
