package com.studenthub.modules.identity.controller.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant expiresAt, CurrentUserResponse user) {
}
