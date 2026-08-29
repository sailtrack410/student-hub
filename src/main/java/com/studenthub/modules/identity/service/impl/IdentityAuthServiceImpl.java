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
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityAuthServiceImpl implements IdentityAuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy";

    private static final String DEFAULT_REQUESTER_ROLE_CODE = "REQUESTER";


    /** 开发测试固定账号的 openid，仅 devWechatLogin 使用 */
    private static final String DEV_FIXED_OPEN_ID = "dev-fixed-openid-0001";

    private final UserMapper userMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final SecurityProperties securityProperties;
    private final CurrentUserProvider currentUserProvider;
    private final WechatIdentityProvider wechatIdentityProvider;
    private final AuthenticatedUserFactory authenticatedUserFactory;
    private final com.studenthub.modules.file.mapper.FileStorageMapper fileStorageMapper;
    private final org.springframework.beans.factory.ObjectProvider<com.studenthub.integration.wechat.WechatPhoneNumberClient> phoneClientProvider;

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
        return loginOrCreateByOpenId(identity.openId(), "微信用户");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse devWechatLogin() {
        return loginOrCreateByOpenId(DEV_FIXED_OPEN_ID, "调试同学");
    }

    private LoginResponse loginOrCreateByOpenId(String openId, String defaultDisplayName) {
        UserEntity user = userMapper.findByWechatOpenId(openId);
        boolean newUser = user == null;
        if (newUser) {
            user = new UserEntity();
            user.setCampusId(wechatIdentityProvider.defaultCampusId());
            user.setUsername("wx_" + openId.substring(0, Math.min(24, openId.length())));
            user.setDisplayName(defaultDisplayName);
            user.setStatus("ENABLED");
            user.setWechatOpenId(openId);
            user.setIdentityType("STUDENT");
            user.setAuthorizationStatus("BASIC");
            userMapper.insert(user);
            bindDefaultRequesterRole(user.getId());
            log.info("首次登录自动创建用户, userId={}", user.getId());
        }
        if (!"ENABLED".equals(user.getStatus())) {
            log.warn("已停用账号尝试登录, userId={}", user.getId());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse updateProfile(String rawToken, AuthenticatedUser current, String displayName,
                                             Long avatarFileId) {
        String normalized = displayName == null ? null : displayName.trim();
        if (normalized != null && (normalized.isEmpty() || normalized.length() > 32)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "昵称长度需为 1-32 个字符");
        }
        if (normalized == null && avatarFileId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "昵称与头像至少提供一项");
        }
        UserEntity user = requireEnabledUser(current.userId());
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        if (normalized != null) {
            update.setDisplayName(normalized);
        }
        if (avatarFileId != null) {
            var file = fileStorageMapper.selectById(avatarFileId);
            if (file == null || !file.getUploaderId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "头像文件不存在或无权使用");
            }
            update.setAvatarUrl(String.valueOf(avatarFileId));
        }
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        AuthenticatedUser refreshed = authenticatedUserFactory.build(reload(user.getId()));
        sessionStore.replace(rawToken, refreshed);
        log.info("用户资料已更新, userId={}, 昵称={}, 头像={}", user.getId(),
                normalized != null, avatarFileId != null);
        return CurrentUserResponse.from(refreshed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse updatePhone(String rawToken, AuthenticatedUser current, String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "手机号格式不正确");
        }
        return persistPhone(rawToken, current, normalized);
    }

    @Override
    public CurrentUserResponse updatePhoneByWechat(String rawToken, AuthenticatedUser current, String jsCode) {
        var phoneClient = phoneClientProvider.getIfAvailable();
        if (phoneClient == null) {
            throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
        }
        String phone = phoneClient.fetchPhone(jsCode);
        return persistPhone(rawToken, current, phone);
    }

    private CurrentUserResponse persistPhone(String rawToken, AuthenticatedUser current, String phone) {
        UserEntity user = requireEnabledUser(current.userId());
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setPhone(phone);
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        AuthenticatedUser refreshed = authenticatedUserFactory.build(reload(user.getId()));
        sessionStore.replace(rawToken, refreshed);
        log.info("用户手机号已更新, userId={}", user.getId());
        return CurrentUserResponse.from(refreshed);
    }


    private UserEntity requireEnabledUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private UserEntity reload(Long userId) {
        return userMapper.selectById(userId);
    }


    private LoginResponse createSession(UserEntity user, boolean newUser) {
        AuthenticatedUser authenticatedUser = authenticatedUserFactory.build(user);
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
