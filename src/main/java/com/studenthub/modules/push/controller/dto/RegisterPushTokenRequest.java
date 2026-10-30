package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "注册系统推送 Token")
public record RegisterPushTokenRequest(
        @Schema(description = "平台：WEB/ANDROID/IOS") @NotBlank
        @Pattern(regexp = "WEB|ANDROID|IOS", message = "platform 仅支持 WEB/ANDROID/IOS") String platform,
        @Schema(description = "推送 Token") @NotBlank String token,
        @Schema(description = "设备标识", nullable = true) String deviceId) {
}
