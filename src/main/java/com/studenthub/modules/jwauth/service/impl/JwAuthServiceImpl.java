package com.studenthub.modules.jwauth.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.jw.KingosoftJwClient;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.AuthenticatedUserFactory;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.jwauth.controller.dto.JwAuthRequest;
import com.studenthub.modules.jwauth.controller.dto.JwAuthResult;
import com.studenthub.modules.jwauth.controller.dto.JwCaptchaResponse;
import com.studenthub.modules.jwauth.controller.dto.JwProfileResponse;
import com.studenthub.modules.jwauth.controller.dto.JwStatusResponse;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.jwauth.security.JwSessionStore.JwSession;

import com.studenthub.modules.jwauth.service.JwAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwAuthServiceImpl implements JwAuthService {

    private static final Set<String> SWITCHABLE_IDENTITIES = Set.of("STUDENT", "TEACHER");

    private final KingosoftJwClient jwClient;
    private final JwProperties jwProperties;
    private final JwBindingMapper jwBindingMapper;
    private final JwSessionStore jwSessionStore;
    private final UserMapper userMapper;
    private final AuthenticatedUserFactory authenticatedUserFactory;
    private final SessionStore sessionStore;

    /** 简易按用户限流：滑动一分钟窗口内的认证尝试次数 */
    private final Map<Long, List<Instant>> authAttempts = new ConcurrentHashMap<>();

    @Override
    public JwCaptchaResponse captcha() {
        String uid = UUID.randomUUID().toString();
        KingosoftJwClient.JwCaptcha captcha = jwClient.fetchCaptcha(uid);
        return new JwCaptchaResponse(captcha.uid(), captcha.imageBase64());
    }

    @Override
    public JwStatusResponse status(Long userId) {
        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        if (binding == null) {
            return new JwStatusResponse(JwStatusResponse.UNBOUND, null, null, null, null, null);
        }
        boolean sessionAlive = jwSessionStore.find(userId).isPresent()
                && binding.getSessionExpiresAt() != null
                && binding.getSessionExpiresAt().isAfter(LocalDateTime.now());
        String status = "AUTHENTICATED".equals(binding.getAuthStatus()) && sessionAlive
                ? JwStatusResponse.AUTHENTICATED : JwStatusResponse.EXPIRED;
        return new JwStatusResponse(status, binding.getSsoAccount(), binding.getJwName(),
                binding.getIdentityKind(), binding.getLastAuthAt(), binding.getSessionExpiresAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JwAuthResult auth(Long userId, String rawToken, JwAuthRequest request) {
        rateLimit(userId);

        // 目标教务账号已被其他用户绑定时直接拒绝，避免无效的远端认证
        JwBindingEntity existingByAccount = jwBindingMapper.findBySsoAccount(request.ssoAccount().trim());
        if (existingByAccount != null && !existingByAccount.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.JW_ACCOUNT_ALREADY_BOUND);
        }

        String account = request.ssoAccount().trim();
        KingosoftJwClient.JwLoginResult loginResult =
                jwClient.login(account, request.password(), request.captchaUid(), request.captchaCode().trim());

        Duration ttl = Duration.ofHours(jwProperties.getSessionTtlHours());
        LocalDateTime now = LocalDateTime.now();

        UserEntity user = requireEnabledUser(userId);
        boolean identityChanged = switchIdentity(user, rawToken, loginResult.identityKind());

        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        if (binding == null) {
            binding = new JwBindingEntity();
            binding.setUserId(userId);
            binding.setCreatedAt(now);
        }
        binding.setSsoAccount(loginResult.ssoAccount());
        binding.setJwName(loginResult.jwName());
        binding.setJwStudentNo(loginResult.jwStudentNo());
        binding.setIdentityKind(loginResult.identityKind());
        binding.setAuthStatus("AUTHENTICATED");
        binding.setLastAuthAt(now);
        binding.setSessionExpiresAt(now.plus(ttl));
        if (binding.getId() == null) {
            try {
                jwBindingMapper.insert(binding);
            } catch (DuplicateKeyException exception) {
                // 并发下另一用户抢先将同一教务账号绑定
                throw new BusinessException(ErrorCode.JW_ACCOUNT_ALREADY_BOUND);
            }
        } else {
            binding.setVersion(binding.getVersion());
            jwBindingMapper.updateById(binding);
        }

        jwSessionStore.save(userId,
                new JwSessionStore.JwSession(loginResult.cookieHeader(), loginResult.homeUrl()), ttl);
        log.info("教务认证成功, userId={}, identityKind={}, identityChanged={}",
                userId, loginResult.identityKind(), identityChanged);

        return new JwAuthResult(JwStatusResponse.AUTHENTICATED, loginResult.ssoAccount(), loginResult.jwName(),
                loginResult.jwStudentNo(), loginResult.identityKind(), identityChanged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId) {
        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        jwBindingMapper.deleteById(binding.getId());
        jwSessionStore.delete(userId);
        log.info("教务绑定已解除, userId={}", userId);
    }

    @Override
    public JwProfileResponse profile(Long userId) {
        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        if (binding == null || !"AUTHENTICATED".equals(binding.getAuthStatus())) {
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        Optional<JwSessionStore.JwSession> session = jwSessionStore.find(userId);
        if (session.isEmpty()) {
            markExpired(binding);
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        try {
            KingosoftJwClient.JwProfile profile = jwClient.fetchProfile(session.get().cookieHeader(),
                    session.get().homeUrl());
            return new JwProfileResponse(profile.ssoAccount(), profile.jwName(),
                    profile.jwStudentNo(), profile.identityKind());
        } catch (BusinessException exception) {
            if (exception.getCode().equals(ErrorCode.JW_SESSION_EXPIRED.getCode())) {
                markExpired(binding);
            }
            throw exception;
        }
    }

    private void markExpired(JwBindingEntity binding) {
        JwBindingEntity update = new JwBindingEntity();
        update.setId(binding.getId());
        update.setAuthStatus("EXPIRED");
        update.setVersion(binding.getVersion());
        jwBindingMapper.updateById(update);
    }

    /** 教务身份映射为平台身份并刷新会话；非学生/教师结果不覆盖现有身份 */
    private boolean switchIdentity(UserEntity user, String rawToken, String identityKind) {
        if (!SWITCHABLE_IDENTITIES.contains(identityKind) || identityKind.equals(user.getIdentityType())) {
            return false;
        }
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setIdentityType(identityKind);
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        AuthenticatedUser refreshed = authenticatedUserFactory.build(reload(user.getId()));
        sessionStore.replace(rawToken, refreshed);
        log.info("教务认证切换平台身份, userId={}, identityType={}", user.getId(), identityKind);
        return true;
    }

    private void rateLimit(Long userId) {
        Instant windowStart = Instant.now().minusSeconds(60);
        List<Instant> attempts = authAttempts.getOrDefault(userId, new java.util.concurrent.CopyOnWriteArrayList<>());
        attempts.removeIf(attempt -> attempt.isBefore(windowStart));
        if (attempts.size() >= jwProperties.getAuthRateLimitPerMinute()) {
            log.info("教务认证触发限流, userId={}", userId);
            throw new BusinessException(ErrorCode.JW_RATE_LIMITED);
        }
        attempts.add(Instant.now());
        authAttempts.put(userId, attempts);
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
}
