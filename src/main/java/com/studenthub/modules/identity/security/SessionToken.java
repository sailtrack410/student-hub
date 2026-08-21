package com.studenthub.modules.identity.security;

import java.time.Instant;

public record SessionToken(String accessToken, Instant expiresAt) {
}
