package com.studenthub.modules.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionStore sessionStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        BearerTokenResolver.resolve(request)
                .flatMap(sessionStore::find)
                .ifPresent(user -> {
                    var authorities = Stream.concat(
                                    user.roleCodes().stream().map(role -> "ROLE_" + role),
                                    user.permissionCodes().stream())
                            .distinct()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        filterChain.doFilter(request, response);
    }
}
