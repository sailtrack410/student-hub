package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.integration.wechat.WechatIdentity;
import com.studenthub.integration.wechat.WechatIdentityProvider;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.RoleScopeRow;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityAuthServiceImpl implements IdentityAuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy";

    private static final String DEFAULT_REQUESTER_ROLE_CODE = "REQUESTER";

    private final UserMapper userMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final SecurityProperties securityProperties;
    private final CurrentUserProvider currentUserProvider;
    private final WechatIdentityProvider wechatIdentityProvider;

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

        return createSession(user, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wechatLogin(String code) {
        WechatIdentity identity = wechatIdentityProvider.exchange(code);
        UserEntity user = userMapper.findByWechatOpenId(identity.openId());
        boolean newUser = user == null;
        if (newUser) {
            user = new UserEntity();
            user.setCampusId(wechatIdentityProvider.defaultCampusId());
            user.setUsername("wx_" + identity.openId().substring(0, Math.min(24, identity.openId().length())));
            user.setDisplayName("微信用户");
            user.setStatus("ENABLED");
            user.setWechatOpenId(identity.openId());
            user.setIdentityType("STUDENT");
            user.setAuthorizationStatus("BASIC");
            userMapper.insert(user);
            bindDefaultRequesterRole(user.getId());
            log.info("首次微信登录自动创建用户, userId={}", user.getId());
        }
        if (!"ENABLED".equals(user.getStatus())) {
            log.warn("已停用账号尝试微信登录, userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return createSession(user, newUser);
    }

    @Override
    public void logout(String rawToken) {
        sessionStore.delete(rawToken);
    }

    @Override
    public CurrentUserResponse currentUser() {
        return CurrentUserResponse.from(currentUserProvider.requireCurrentUser());
    }

    private LoginResponse createSession(UserEntity user, boolean newUser) {
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
                user.getIdentityType(),
                user.getAuthorizationStatus(),
                roleScopes,
                permissionCodes);
        SessionToken token = sessionStore.create(authenticatedUser, securityProperties.getSessionTtl());

        UserEntity loginUpdate = new UserEntity();
        loginUpdate.setId(user.getId());
        loginUpdate.setLastLoginAt(LocalDateTime.now());
        loginUpdate.setVersion(user.getVersion());
        userMapper.updateById(loginUpdate);

        log.info("用户登录成功, userId={}, roles={}", user.getId(), authenticatedUser.roleCodes());
        return new LoginResponse(token.accessToken(), token.expiresAt(), newUser,
                CurrentUserResponse.from(authenticatedUser));
    }

    private RoleScope toRoleScope(RoleScopeRow row) {
        return new RoleScope(row.getRoleCode(), row.getOrganizationId(), row.getDataScope());
    }

    private void bindDefaultRequesterRole(Long userId) {
        RoleEntity requesterRole = roleMapper.findByRoleCode(DEFAULT_REQUESTER_ROLE_CODE);
        if (requesterRole == null) {
            log.error("默认 REQUESTER 角色不存在, 无法完成用户注册, userId={}", userId);
            throw new BusinessException(ErrorCode.ROLE_NOT_CONFIGURED);
        }
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(requesterRole.getId());
        userRole.setDataScope("SELF");
        userRole.setCreatedAt(LocalDateTime.now());
        userRole.setCreatedBy(0L);
        userRoleMapper.insert(userRole);
    }
}
