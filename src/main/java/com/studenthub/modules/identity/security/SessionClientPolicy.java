package com.studenthub.modules.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 会话有效期策略：按客户端类型区分。
 *
 * <p>App（学生端）走长期免登录——默认 30 天 TTL + 剩余不足 7 天时滑动续期，活跃用户不会被要求重新登录；
 * Web/管理台与无法识别的客户端一律使用短 TTL（默认 8 小时）且不续期，避免管理端会话被意外延长。
 *
 * <p>识别依据是请求头（默认 {@code x-client-type: app}）。把默认值定为"短 TTL"是刻意选择：
 * 漏发头的客户端只会更安全，不会更宽松。
 */
@Component
@RequiredArgsConstructor
public class SessionClientPolicy {

    private final SecurityProperties properties;

    /** 续期参数；{@link #enabled()} 为 false 表示该客户端不续期 */
    public record Renewal(Duration ttl, Duration threshold) {
        public boolean enabled() {
            return ttl != null && !ttl.isZero() && !ttl.isNegative()
                    && threshold != null && !threshold.isZero() && !threshold.isNegative();
        }
    }

    private static final Renewal DISABLED = new Renewal(Duration.ZERO, Duration.ZERO);

    /** 依据具体请求判定是否为 App 客户端 */
    public boolean isAppClient(HttpServletRequest request) {
        String header = properties.getAppClientHeader();
        String expected = properties.getAppClientValue();
        if (request == null || header == null || header.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        String value = request.getHeader(header);
        return value != null && expected.equalsIgnoreCase(value.trim());
    }

    /** 依据当前线程绑定的请求判定（登录创建会话时使用）；无请求上下文时按非 App 处理 */
    public boolean isAppClient() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null && isAppClient(attributes.getRequest());
    }

    /** 创建会话时应使用的 TTL */
    public Duration creationTtl() {
        return isAppClient() ? properties.getAppSessionTtl() : properties.getSessionTtl();
    }

    /** 请求对应的续期参数 */
    public Renewal renewalFor(HttpServletRequest request) {
        if (!isAppClient(request)) {
            return DISABLED;
        }
        return new Renewal(properties.getAppSessionTtl(), properties.getSessionRenewThreshold());
    }
}
