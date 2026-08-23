package com.studenthub.modules.identity.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "登录成功响应")
public record LoginResponse(
        @Schema(description = "不透明访问令牌，仅用于 Authorization: Bearer 请求头", example = "opaque-token")
        String accessToken,
        @Schema(description = "访问令牌过期时间", example = "2026-08-23T18:00:00Z")
        Instant expiresAt,
        @Schema(description = "是否为首次登录并自动创建用户", example = "true")
        @JsonProperty("isNewUser") boolean isNewUser,
        @Schema(description = "当前用户信息")
        CurrentUserResponse user) {
}
