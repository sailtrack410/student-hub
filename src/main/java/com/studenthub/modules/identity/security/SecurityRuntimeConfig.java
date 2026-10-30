package com.studenthub.modules.identity.security;

import com.studenthub.modules.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录与安全策略的运行时读取器。
 *
 * <p>配置中心里的 {@code security.*} 不是只展示的文案：登录注册的密码校验、会话有效期、
 * 单用户设备上限和跨域白名单都在运行时读取这里。配置保存后下一次请求/登录立即生效，
 * 已签发的旧会话不会被追溯修改（会话 TTL 只影响新登录）；管理端修改密码后，旧密码
 * 同样不会让已登录会话失效，需要管理员按设备踢线。
 *
 * <p>读取失败时回退到 {@link SecurityProperties} / 代码默认值，不允许因为配置表临时不可用
 * 把安全策略放宽到“无密码规则 / 任意跨域 / 无限设备”。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityRuntimeConfig {

    public static final String KEY_PASSWORD_MIN_LENGTH = "security.password_min_length";
    public static final String KEY_PASSWORD_REQUIRE_COMPLEX = "security.password_require_complex";
    public static final String KEY_LOGIN_CAPTCHA_ENABLED = "security.login_captcha_enabled";
    public static final String KEY_MAX_DEVICES_PER_USER = "security.max_devices_per_user";
    public static final String KEY_SESSION_TTL_HOURS = "security.session_ttl_hours";
    public static final String KEY_CORS_ALLOWED_ORIGINS = "security.cors_allowed_origins";

    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;
    private static final int MAX_PASSWORD_MIN_LENGTH = 64;
    private static final int DEFAULT_SESSION_TTL_HOURS = 8;
    private static final int MAX_SESSION_TTL_HOURS = 24 * 7;
    private static final int DEFAULT_MAX_DEVICES = 5;
    private static final int MAX_DEVICES_LIMIT = 20;

    private final SystemConfigService systemConfigService;
    private final SecurityProperties securityProperties;

    public int passwordMinLength() {
        int configured = intValue(KEY_PASSWORD_MIN_LENGTH, DEFAULT_PASSWORD_MIN_LENGTH);
        return Math.max(6, Math.min(MAX_PASSWORD_MIN_LENGTH, configured));
    }

    public boolean passwordRequireComplex() {
        return booleanValue(KEY_PASSWORD_REQUIRE_COMPLEX, true);
    }

    public boolean loginCaptchaEnabled() {
        return booleanValue(KEY_LOGIN_CAPTCHA_ENABLED, false);
    }

    /** 0 表示不限制；正常配置范围 1–20。 */
    public int maxDevicesPerUser() {
        int configured = intValue(KEY_MAX_DEVICES_PER_USER, DEFAULT_MAX_DEVICES);
        return Math.max(0, Math.min(MAX_DEVICES_LIMIT, configured));
    }

    /** Web/管理端会话时长；App 的长期会话仍使用 {@link SecurityProperties#getAppSessionTtl()}。 */
    public Duration platformSessionTtl(Duration fallback) {
        int configured = intValue(KEY_SESSION_TTL_HOURS,
                fallback == null ? DEFAULT_SESSION_TTL_HOURS : (int) Math.max(1, fallback.toHours()));
        int hours = Math.max(1, Math.min(MAX_SESSION_TTL_HOURS, configured));
        return Duration.ofHours(hours);
    }

    /**
     * 管理端 CORS 白名单：配置中心非空时覆盖环境变量；为空时沿用
     * {@code studenthub.security.allowed-origins}，避免上线配置切换导致现有域名全部失效。
     */
    public List<String> corsAllowedOrigins() {
        String raw = value(KEY_CORS_ALLOWED_ORIGINS);
        if (!StringUtils.hasText(raw)) {
            return normalizeOrigins(securityProperties.getAllowedOrigins());
        }
        List<String> parsed = normalizeOrigins(List.of(raw.split(",")));
        return parsed.isEmpty() ? normalizeOrigins(securityProperties.getAllowedOrigins()) : parsed;
    }

    private List<String> normalizeOrigins(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String origin : origins) {
            if (!StringUtils.hasText(origin)) {
                continue;
            }
            String value = origin.trim();
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    private String value(String key) {
        try {
            return systemConfigService.getValue(key, null);
        } catch (RuntimeException exception) {
            log.warn("读取安全运行时配置失败，使用安全默认值, key={}, error={}", key, exception.getMessage());
            return null;
        }
    }

    private int intValue(String key, int fallback) {
        String raw = value(key);
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            log.warn("安全配置不是整数，使用默认值, key={}, value={}", key, raw);
            return fallback;
        }
    }

    private boolean booleanValue(String key, boolean fallback) {
        String raw = value(key);
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }
}
