package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.PhoneNumbers;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.AccountCredentialsResponse;
import com.studenthub.modules.identity.controller.dto.BindEmailRequest;
import com.studenthub.modules.identity.controller.dto.EmailCodeResponse;
import com.studenthub.modules.identity.controller.dto.RegisterRequest;
import com.studenthub.modules.identity.controller.dto.ResetPasswordRequest;
import com.studenthub.modules.identity.controller.dto.SendEmailCodeRequest;
import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.UpdateAccountCredentialsRequest;
import com.studenthub.modules.identity.controller.dto.GrowthSummary;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.integration.wechat.WechatIdentity;
import com.studenthub.integration.wechat.WechatIdentityProvider;
import com.studenthub.integration.qq.QqIdentity;
import com.studenthub.integration.qq.QqIdentityProvider;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.RoleScopeRow;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.security.LoginCaptchaService;
import com.studenthub.modules.identity.security.SecurityRuntimeConfig;
import com.studenthub.modules.identity.security.RoleScope;
import com.studenthub.modules.identity.security.SecurityProperties;
import com.studenthub.modules.identity.security.SessionClientPolicy;
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.security.SessionToken;
import com.studenthub.modules.identity.service.IdentityAuthService;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityAuthServiceImpl implements IdentityAuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy";

    private static final String DEFAULT_REQUESTER_ROLE_CODE = "REQUESTER";

    /** 密码注册账号的默认昵称前缀，后缀为 6 位随机数字。 */
    private static final String DEFAULT_NICKNAME_PREFIX = "用户";


    /** 开发测试固定账号的 openid，仅 devWechatLogin 使用 */
    private static final String DEV_FIXED_OPEN_ID = "dev-fixed-openid-0001";

    private final UserMapper userMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final SecurityProperties securityProperties;
    private final SessionClientPolicy sessionClientPolicy;
    private final SecurityRuntimeConfig securityRuntimeConfig;
    private final LoginCaptchaService loginCaptchaService;
    private final CurrentUserProvider currentUserProvider;
    private final WechatIdentityProvider wechatIdentityProvider;
    private final QqIdentityProvider qqIdentityProvider;
    private final AuthenticatedUserFactory authenticatedUserFactory;
    private final com.studenthub.modules.file.mapper.FileStorageMapper fileStorageMapper;
    private final FileStorageService fileStorageService;
    private final com.studenthub.integration.mail.SmtpEmailCodeSender emailCodeSender;
    private final org.springframework.beans.factory.ObjectProvider<com.studenthub.integration.wechat.WechatPhoneNumberClient> phoneClientProvider;
    private final com.studenthub.modules.achievement.service.AchievementService achievementService;
    private final com.studenthub.modules.device.service.DeviceSessionService deviceSessionService;
    private final com.studenthub.modules.jwauth.mapper.JwBindingMapper jwBindingMapper;
    private final AuditLogService auditLogService;

    private static final Map<String, EmailCode> EMAIL_CODES = new ConcurrentHashMap<>();
    private record EmailCode(String code, LocalDateTime expiresAt) {}

    @Override
    public LoginResponse login(String username, String password) {
        return login(username, password, null, null);
    }

    @Override
    public LoginResponse login(String username, String password, String captchaId, String captchaCode) {
        if (securityRuntimeConfig.loginCaptchaEnabled()) {
            try {
                loginCaptchaService.verify(captchaId, captchaCode);
            } catch (BusinessException exception) {
                auditLogService.recordFailure(null, username, "LOGIN_FAILED", "AUTH", username,
                        "图形验证码错误或已过期", Map.of("username", username));
                throw exception;
            }
        }
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        String passwordHash = user == null || user.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH
                : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (user == null || !passwordMatches) {
            log.warn("密码登录失败");
            auditLogService.recordFailure(null, username, "LOGIN_FAILED", "AUTH", username,
                    "用户名或密码错误", Map.of("username", username));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!"ENABLED".equals(user.getStatus())) {
            log.warn("已停用账号尝试登录, userId={}", user.getId());
            auditLogService.recordFailure(user.getId(), user.getDisplayName(), "LOGIN_FAILED", "AUTH",
                    String.valueOf(user.getId()), "账号已停用", Map.of("username", user.getUsername()));
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        return createSession(user, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wechatLogin(String code) {
        WechatIdentity identity = wechatIdentityProvider.exchange(code);
        return loginOrCreateByIdentity("WECHAT", identity.openId(), null, "微信用户", null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse qqLogin(String accessToken, String openId) {
        QqIdentity identity = qqIdentityProvider.exchange(accessToken);
        // 客户端提交的 openId 可伪造；只接受与 QQ 反查结果一致的提交值，不一致按登录失败处理
        if (openId != null && !openId.isBlank() && !openId.equals(identity.openId())) {
            log.warn("QQ openId 与反查结果不一致, 拒绝登录");
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        return loginOrCreateByIdentity("QQ", identity.openId(), identity.unionId(), "QQ用户",
                identity.nickname(), identity.avatarUrl());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse bindQq(String rawToken, AuthenticatedUser current, String accessToken, String openId) {
        QqIdentity identity = qqIdentityProvider.exchange(accessToken);
        if (openId != null && !openId.isBlank() && !openId.equals(identity.openId())) {
            log.warn("QQ 绑定 openId 与反查结果不一致, 拒绝绑定");
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        UserEntity user = requireEnabledUser(current.userId());
        UserEntity bound = userMapper.findByQqOpenId(identity.openId());
        if (bound != null && !bound.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该 QQ 已绑定其他账号");
        }
        if (user.getQqOpenId() != null && !user.getQqOpenId().isBlank()
                && !user.getQqOpenId().equals(identity.openId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前账号已绑定其他 QQ，暂不支持更换");
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setQqOpenId(identity.openId());
        if (identity.unionId() != null && !identity.unionId().isBlank()) {
            update.setQqUnionId(identity.unionId());
        }
        // 仅在用户没有主动设置过昵称/头像时，用 QQ 资料覆盖默认值。
        if (shouldApplyDefault(identity.nickname(), user.getDisplayNameCustomized())
                && !identity.nickname().equals(user.getDisplayName())) {
            update.setDisplayName(identity.nickname());
        }
        if (shouldApplyDefault(identity.avatarUrl(), user.getAvatarCustomized())
                && !identity.avatarUrl().equals(user.getAvatarUrl())) {
            update.setAvatarUrl(identity.avatarUrl());
        }
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        AuthenticatedUser refreshed = authenticatedUserFactory.build(reload(user.getId()));
        sessionStore.replace(rawToken, refreshed);
        log.info("QQ 绑定成功, userId={}, 昵称已覆盖={}, 头像已覆盖={}", user.getId(),
                update.getDisplayName() != null, update.getAvatarUrl() != null);
        return CurrentUserResponse.from(refreshed, fileStorageService.resolveAvatarUrl(refreshed.avatarFileId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse devWechatLogin() {
        return loginOrCreateByIdentity("WECHAT", DEV_FIXED_OPEN_ID, null, "调试同学", null, null);
    }

    /**
     * 按 provider 身份登录或建号：openId 只在对应身份列中查找（各 provider 的 openId
     * 互不通用），新号 username 用 provider 小写前缀，授权态/默认角色与微信登录一致。
     * QQ 资料仅在用户未主动自定义昵称/头像时覆盖默认值。
     */
    private LoginResponse loginOrCreateByIdentity(String provider, String openId, String unionId,
                                                  String defaultDisplayName, String qqNickname,
                                                  String qqAvatarUrl) {
        UserEntity user = "QQ".equals(provider)
                ? userMapper.findByQqOpenId(openId)
                : userMapper.findByWechatOpenId(openId);
        boolean newUser = user == null;
        if (newUser) {
            user = new UserEntity();
            user.setCampusId("QQ".equals(provider)
                    ? qqIdentityProvider.defaultCampusId()
                    : wechatIdentityProvider.defaultCampusId());
            user.setUsername(provider.toLowerCase() + "_" + openId.substring(0, Math.min(24, openId.length())));
            user.setDisplayName(firstNonBlank(qqNickname, defaultDisplayName));
            if (qqAvatarUrl != null && !qqAvatarUrl.isBlank()) {
                user.setAvatarUrl(qqAvatarUrl);
            }
            user.setDisplayNameCustomized(false);
            user.setAvatarCustomized(false);
            user.setStatus("ENABLED");
            if ("QQ".equals(provider)) {
                user.setQqOpenId(openId);
                user.setQqUnionId(unionId);
            } else {
                user.setWechatOpenId(openId);
            }
            user.setIdentityType("STUDENT");
            user.setAuthorizationStatus("BASIC");
            userMapper.insert(user);
            bindDefaultRequesterRole(user.getId());
            log.info("首次登录自动创建用户, userId={}, provider={}", user.getId(), provider);
        } else if ("QQ".equals(provider)) {
            boolean changed = false;
            if (unionId != null && !unionId.isBlank()
                    && (user.getQqUnionId() == null || user.getQqUnionId().isBlank())) {
                // 应用后续开通 UnionID 机制时，老账号首次再登录补齐，便于将来多端统一身份。
                user.setQqUnionId(unionId);
                changed = true;
            }
            if (applyQqProfileIfNotCustomized(user, qqNickname, qqAvatarUrl)) {
                changed = true;
            }
            if (changed) {
                userMapper.updateById(user);
            }
        }
        if (!"ENABLED".equals(user.getStatus())) {
            log.warn("已停用账号尝试登录, userId={}", user.getId());
            auditLogService.recordFailure(user.getId(), user.getDisplayName(), "LOGIN_FAILED", "AUTH",
                    String.valueOf(user.getId()), "账号已停用", Map.of("username", user.getUsername()));
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return createSession(user, newUser);
    }

    /** 用 QQ 资料覆盖仍为默认值的昵称/头像；返回是否发生变化。 */
    private boolean applyQqProfileIfNotCustomized(UserEntity user, String nickname, String avatarUrl) {
        boolean changed = false;
        if (shouldApplyDefault(nickname, user.getDisplayNameCustomized())
                && !nickname.equals(user.getDisplayName())) {
            user.setDisplayName(nickname);
            changed = true;
        }
        if (shouldApplyDefault(avatarUrl, user.getAvatarCustomized())
                && !avatarUrl.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(avatarUrl);
            changed = true;
        }
        return changed;
    }

    private boolean shouldApplyDefault(String value, Boolean customized) {
        return value != null && !value.isBlank() && !Boolean.TRUE.equals(customized);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    @Override
    public void logout(String rawToken) {
        sessionStore.delete(rawToken);
    }

    @Override
    public CurrentUserResponse currentUser() {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        UserEntity entity = userMapper.selectById(user.userId());
        String uid = null;
        String wornBadgeCode = null;
        if (entity != null) {
            wornBadgeCode = entity.getWornBadgeCode();
            JwBindingEntity binding = jwBindingMapper.findByUserId(user.userId());
            uid = binding != null ? binding.getJwStudentNo() : null;
        }
        int exp = achievementService.totalExpOf(user.userId());
        int level = com.studenthub.modules.achievement.service.AchievementService.levelOf(exp);
        Integer nextLevelExp = null;
        Integer expToNext = null;
        if (level < com.studenthub.modules.achievement.service.AchievementService.maxLevel()) {
            nextLevelExp = com.studenthub.modules.achievement.service.AchievementService.levelExp(level + 1);
            expToNext = nextLevelExp - exp;
        }
        GrowthSummary growth = new GrowthSummary(uid, level,
                com.studenthub.modules.achievement.service.AchievementService.levelName(level),
                exp, nextLevelExp, expToNext, wornBadgeCode);
        return new CurrentUserResponse(user.userId(), user.campusId(), user.defaultOrganizationId(),
                user.username(), user.displayName(), user.identityType(), user.authorizationStatus(),
                user.roleCodes(), user.roleScopes(), user.permissionCodes(),
                fileStorageService.resolveAvatarUrl(entity == null ? user.avatarFileId() : entity.getAvatarUrl()),
                user.phone(), growth);
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
            update.setDisplayNameCustomized(true);
        }
        if (avatarFileId != null) {
            if (avatarFileId == 0L) {
                // 0 = 恢复默认头像（清空绑定），并允许以后 QQ 登录重新使用 QQ 头像
                update.setAvatarUrl("");
                update.setAvatarCustomized(false);
            } else {
                var file = fileStorageMapper.selectById(avatarFileId);
                if (file == null || !file.getUploaderId().equals(user.getId())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "头像文件不存在或无权使用");
                }
                update.setAvatarUrl(String.valueOf(avatarFileId));
                update.setAvatarCustomized(true);
            }
        }
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        AuthenticatedUser refreshed = authenticatedUserFactory.build(reload(user.getId()));
        sessionStore.replace(rawToken, refreshed);
        log.info("用户资料已更新, userId={}, 昵称={}, 头像={}", user.getId(),
                normalized != null, avatarFileId != null);
        return CurrentUserResponse.from(refreshed, fileStorageService.resolveAvatarUrl(refreshed.avatarFileId()));
    }

    @Override
    public AccountCredentialsResponse credentials(AuthenticatedUser current) {
        UserEntity user = requireEnabledUser(current.userId());
        boolean passwordSet = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        boolean qqBound = user.getQqOpenId() != null && !user.getQqOpenId().isBlank();
        return new AccountCredentialsResponse(user.getUsername(), passwordSet, user.getEmail(), qqBound);
    }

    @Override
    public EmailCodeResponse sendEmailCode(SendEmailCodeRequest request) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        validateEmail(email);
        String purpose = request.purpose() == null ? "" : request.purpose().trim().toUpperCase();
        if (!Set.of("REGISTER", "RESET", "BIND").contains(purpose)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "验证码用途不合法");
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        boolean devCodeVisible = emailCodeSender.devCodeVisible();
        if (!emailCodeSender.isConfigured()) {
            if (!devCodeVisible) {
                // 生产环境没配 SMTP 时必须明确失败，不能假装验证码已发送。
                log.warn("邮箱验证码发送被拒绝：邮件服务未配置, purpose={}", purpose);
                throw new BusinessException(ErrorCode.EMAIL_NOT_CONFIGURED);
            }
            EMAIL_CODES.put(purpose + ":" + email, new EmailCode(code, LocalDateTime.now().plusMinutes(10)));
            log.info("开发/测试环境未配置邮件服务，验证码仅在响应中返回, email={}, purpose={}", maskEmail(email), purpose);
            return new EmailCodeResponse(email, code, "开发环境未配置邮件服务，验证码已直接返回");
        }
        emailCodeSender.sendVerificationCode(email, code, purpose);
        EMAIL_CODES.put(purpose + ":" + email, new EmailCode(code, LocalDateTime.now().plusMinutes(10)));
        log.info("邮箱验证码已生成并发送, email={}, purpose={}", maskEmail(email), purpose);
        return new EmailCodeResponse(email, devCodeVisible ? code : null,
                devCodeVisible ? "验证码已发送至邮箱（开发环境同时返回验证码）" : "验证码已发送至邮箱，请查收");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        String accountId = request.accountId() == null ? "" : request.accountId().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        String password = request.password() == null ? "" : request.password();
        validateEmail(email);
        verifyEmailCode("REGISTER", email, request.code());
        validateAccountAndPassword(accountId, password);
        Long dupAccount = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, accountId));
        if (dupAccount != null && dupAccount > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "登录 ID 已被使用");
        }
        Long dupEmail = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email));
        if (dupEmail != null && dupEmail > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "邮箱已绑定其他账号");
        }
        UserEntity user = new UserEntity();
        user.setCampusId(wechatIdentityProvider.defaultCampusId());
        user.setUsername(accountId);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(generateDefaultNickname());
        user.setDisplayNameCustomized(false);
        user.setAvatarCustomized(false);
        user.setEmail(email);
        user.setStatus("ENABLED");
        user.setIdentityType("STUDENT");
        user.setAuthorizationStatus("BASIC");
        userMapper.insert(user);
        bindDefaultRequesterRole(user.getId());
        EMAIL_CODES.remove("REGISTER:" + email);
        log.info("账号注册成功, userId={}, accountId={}", user.getId(), accountId);
        return createSession(user, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        String accountId = request.accountId() == null ? "" : request.accountId().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        validateEmail(email);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, accountId));
        if (user == null || !email.equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "账号与邮箱不匹配");
        }
        verifyEmailCode("RESET", email, request.code());
        String password = request.newPassword() == null ? "" : request.newPassword();
        validatePassword(password);
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(password));
        update.setVersion(user.getVersion());
        userMapper.updateById(update);
        EMAIL_CODES.remove("RESET:" + email);
        log.info("找回密码成功, userId={}", user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialsResponse bindEmail(String rawToken, AuthenticatedUser current,
                                                BindEmailRequest request) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        validateEmail(email);
        verifyEmailCode("BIND", email, request.code());
        UserEntity user = requireEnabledUser(current.userId());
        Long dupEmail = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .ne(UserEntity::getId, user.getId()));
        if (dupEmail != null && dupEmail > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "邮箱已绑定其他账号");
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setEmail(email);
        update.setVersion(user.getVersion());
        userMapper.updateById(update);
        EMAIL_CODES.remove("BIND:" + email);
        boolean passwordSet = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        boolean qqBound = user.getQqOpenId() != null && !user.getQqOpenId().isBlank();
        return new AccountCredentialsResponse(user.getUsername(), passwordSet, email, qqBound);
    }

    private void validateEmail(String email) {
        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "邮箱格式不正确");
        }
    }

    private void validateAccountAndPassword(String accountId, String password) {
        if (!accountId.matches("^[A-Za-z0-9_]{4,20}$")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "账号需为 4-20 位字母、数字或下划线");
        }
        validatePassword(password);
    }

    private void validatePassword(String password) {
        String value = password == null ? "" : password;
        int minLength = securityRuntimeConfig.passwordMinLength();
        if (value.length() < minLength || value.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "密码长度需为 " + minLength + "-128 位");
        }
        if (!value.matches("^[A-Za-z0-9!@#$%^&*._-]+$")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "密码只能包含字母、数字及 !@#$%^&*._-");
        }
        if (securityRuntimeConfig.passwordRequireComplex()
                && (!value.matches(".*[A-Za-z].*") || !value.matches(".*\\d.*"))) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "密码需同时包含字母和数字");
        }
    }

    private void verifyEmailCode(String purpose, String email, String code) {
        EmailCode entry = EMAIL_CODES.get(purpose + ":" + email);
        if (entry == null || entry.expiresAt().isBefore(LocalDateTime.now()) || !entry.code().equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "验证码不正确或已过期");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialsResponse updateCredentials(String rawToken, AuthenticatedUser current,
                                                        UpdateAccountCredentialsRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        if (!username.matches("^[A-Za-z0-9_]{4,20}$")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "账号需为 4-20 位字母、数字或下划线");
        }
        validatePassword(password);
        UserEntity user = requireEnabledUser(current.userId());
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            String currentPassword = request.currentPassword() == null ? "" : request.currentPassword();
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "当前密码不正确");
            }
        }
        Long duplicate = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)
                .ne(UserEntity::getId, user.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该账号已被使用");
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setUsername(username);
        update.setPasswordHash(passwordEncoder.encode(password));
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        AuthenticatedUser refreshed = authenticatedUserFactory.build(reload(user.getId()));
        sessionStore.replace(rawToken, refreshed);
        log.info("登录账号与密码已设置, userId={}, username={}", user.getId(), username);
        boolean qqBound = user.getQqOpenId() != null && !user.getQqOpenId().isBlank();
        return new AccountCredentialsResponse(username, true, user.getEmail(), qqBound);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse updatePhone(String rawToken, AuthenticatedUser current, String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!PhoneNumbers.isValid(normalized)) {
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
        return CurrentUserResponse.from(refreshed, fileStorageService.resolveAvatarUrl(refreshed.avatarFileId()));
    }

    private String generateDefaultNickname() {
        return DEFAULT_NICKNAME_PREFIX + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
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
        // App 端使用长期 TTL（配合滑动续期实现免登录）；Web/管理端 TTL 从配置中心读取，默认 8 小时。
        Duration ttl = sessionClientPolicy.creationTtl();
        if (!sessionClientPolicy.isAppClient()) {
            ttl = securityRuntimeConfig.platformSessionTtl(ttl);
        }
        SessionToken token = sessionStore.create(authenticatedUser, ttl);
        registerDeviceQuietly(user.getId(), token.accessToken());
        enforceDeviceLimitQuietly(user.getId());

        UserEntity loginUpdate = new UserEntity();
        loginUpdate.setId(user.getId());
        loginUpdate.setLastLoginAt(LocalDateTime.now());
        loginUpdate.setVersion(user.getVersion());
        userMapper.updateById(loginUpdate);

        log.info("用户登录成功, userId={}, roles={}", user.getId(), authenticatedUser.roleCodes());
        auditLogService.record(user.getId(), user.getDisplayName(), "LOGIN_SUCCESS", "AUTH",
                String.valueOf(user.getId()), null,
                Map.of("username", user.getUsername(), "roles", authenticatedUser.roleCodes()));
        return new LoginResponse(token.accessToken(), token.expiresAt(), newUser,
                CurrentUserResponse.from(authenticatedUser,
                        fileStorageService.resolveAvatarUrl(authenticatedUser.avatarFileId())));
    }

    /** 登录设备登记（API需求清单 3.x）：失败不影响登录主流程 */
    private void registerDeviceQuietly(Long userId, String rawToken) {
        try {
            var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)) {
                return;
            }
            jakarta.servlet.http.HttpServletRequest request = servlet.getRequest();
            String clientType = sessionClientPolicy.isAppClient(request) ? "APP" : "WEB";
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.getHeader("X-Real-IP");
            }
            deviceSessionService.register(userId, rawToken, request.getHeader("User-Agent"), clientType,
                    ip == null || ip.isBlank() ? request.getRemoteAddr() : ip.split(",")[0].trim());
        } catch (RuntimeException exception) {
            log.warn("登录设备登记失败（不影响登录）, userId={}, error={}", userId, exception.getMessage());
        }
    }


    /** 单用户在线设备上限：保留最新设备并踢掉最旧会话，避免新设备无法登录。 */
    private void enforceDeviceLimitQuietly(Long userId) {
        try {
            int maxDevices = securityRuntimeConfig.maxDevicesPerUser();
            if (maxDevices <= 0) {
                return;
            }
            int revoked = deviceSessionService.enforceDeviceLimit(userId, maxDevices);
            if (revoked > 0) {
                log.info("登录设备数超过上限，已踢除最旧设备, userId={}, max={}, revoked={}",
                        userId, maxDevices, revoked);
            }
        } catch (RuntimeException exception) {
            log.warn("登录设备上限校验失败（不影响登录）, userId={}, error={}", userId, exception.getMessage());
        }
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
