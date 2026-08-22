package com.studenthub.modules.identity.security;

import java.util.List;

public record AuthenticatedUser(
        Long userId,
        Long campusId,
        Long defaultOrganizationId,
        String username,
        String displayName,
        String identityType,
        String authorizationStatus,
        List<RoleScope> roleScopes,
        List<String> permissionCodes) {

    public List<String> roleCodes() {
        return roleScopes.stream().map(RoleScope::roleCode).distinct().toList();
    }
}
