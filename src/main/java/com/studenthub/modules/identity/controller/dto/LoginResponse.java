package com.studenthub.modules.identity.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant expiresAt,
        @JsonProperty("isNewUser") boolean isNewUser,
        CurrentUserResponse user) {
}
