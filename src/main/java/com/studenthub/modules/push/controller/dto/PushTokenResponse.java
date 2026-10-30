package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "推送 Token")
public record PushTokenResponse(Long id, String platform, String deviceId, LocalDateTime lastSeenAt) {
}
