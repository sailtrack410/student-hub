package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.RoleScope;

import java.util.List;

public record CurrentUserResponse(
        Long userId,
        Long campusId,
        Long defaultOrganizationId,
        String username,
        String displayName,
        String identityType,
        String authorizationStatus,
        List<String> roles,
        List<RoleScope> roleScopes,
        List<String> permissions) {

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
                user.permissionCodes());
    }
}
