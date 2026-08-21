package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.RoleScopeRow;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.security.RoleScope;
import com.studenthub.modules.identity.security.SecurityProperties;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.security.SessionToken;
import com.studenthub.modules.identity.service.IdentityAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityAuthServiceImpl implements IdentityAuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy";

    private final UserMapper userMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final SecurityProperties securityProperties;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public LoginResponse login(String username, String password) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        String passwordHash = user == null || user.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH
                : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (user == null || !passwordMatches) {
            log.warn("密码登录失败");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!"ENABLED".equals(user.getStatus())) {
            log.warn("已停用账号尝试登录, userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        List<RoleScope> roleScopes = identityAccessMapper.findRoleScopes(user.getId()).stream()
                .map(this::toRoleScope)
                .toList();
        List<String> permissionCodes = identityAccessMapper.findPermissionCodes(user.getId());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getCampusId(),
                user.getDefaultOrganizationId(),
                user.getUsername(),
                user.getDisplayName(),
                roleScopes,
                permissionCodes);
        SessionToken token = sessionStore.create(authenticatedUser, securityProperties.getSessionTtl());

        UserEntity loginUpdate = new UserEntity();
        loginUpdate.setId(user.getId());
        loginUpdate.setLastLoginAt(LocalDateTime.now());
        loginUpdate.setVersion(user.getVersion());
        userMapper.updateById(loginUpdate);

        log.info("用户登录成功, userId={}, roles={}", user.getId(), authenticatedUser.roleCodes());
        return new LoginResponse(token.accessToken(), token.expiresAt(), CurrentUserResponse.from(authenticatedUser));
    }

    @Override
    public void logout(String rawToken) {
        sessionStore.delete(rawToken);
    }

    @Override
    public CurrentUserResponse currentUser() {
        return CurrentUserResponse.from(currentUserProvider.requireCurrentUser());
    }

    private RoleScope toRoleScope(RoleScopeRow row) {
        return new RoleScope(row.getRoleCode(), row.getOrganizationId(), row.getDataScope());
    }
}
