package com.studenthub.modules.identity.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public final class BearerTokenResolver {

    private static final String PREFIX = "Bearer ";

    private BearerTokenResolver() {
    }

    public static Optional<String> resolve(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
