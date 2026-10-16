package com.studenthub.modules.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionStore sessionStore;
    private final SessionClientPolicy sessionClientPolicy;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        BearerTokenResolver.resolve(request)
                .ifPresent(rawToken -> sessionStore.find(rawToken).ifPresent(user -> {
                    var authorities = Stream.concat(
                                    user.roleCodes().stream().map(role -> "ROLE_" + role),
                                    user.permissionCodes().stream())
                            .distinct()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    renewIfNeeded(request, rawToken, user);
                }));
        filterChain.doFilter(request, response);
    }

    /** App 端滑动续期：剩余有效期不足时刷新，避免活跃学生被要求重新登录 */
    private void renewIfNeeded(HttpServletRequest request, String rawToken, AuthenticatedUser user) {
        SessionClientPolicy.Renewal renewal = sessionClientPolicy.renewalFor(request);
        if (!renewal.enabled()) {
            return;
        }
        try {
            sessionStore.renew(rawToken, user.userId(), renewal.ttl(), renewal.threshold());
        } catch (RuntimeException exception) {
            // 续期失败不应影响本次请求：会话仍按原有效期有效，下次请求会再次尝试
            log.warn("会话滑动续期失败, userId={}, error={}", user.userId(), exception.getMessage());
        }
    }
}
