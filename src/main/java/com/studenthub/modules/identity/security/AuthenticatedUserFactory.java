package com.studenthub.modules.identity.security;

import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 由用户实体构建会话内认证主体；登录、资料修改、教务认证切换身份等场景共用。
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserFactory {

    private final IdentityAccessMapper identityAccessMapper;

    public AuthenticatedUser build(UserEntity user) {
        List<RoleScope> roleScopes = identityAccessMapper.findRoleScopes(user.getId()).stream()
                .map(row -> new RoleScope(row.getRoleCode(), row.getOrganizationId(), row.getDataScope()))
                .toList();
        List<String> permissionCodes = identityAccessMapper.findPermissionCodes(user.getId());
        return new AuthenticatedUser(
                user.getId(),
                user.getCampusId(),
                user.getDefaultOrganizationId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getIdentityType(),
                user.getAuthorizationStatus(),
                roleScopes,
                permissionCodes);
    }
}
