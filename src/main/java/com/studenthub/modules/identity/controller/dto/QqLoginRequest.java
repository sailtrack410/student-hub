package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "QQ 登录请求：accessToken 为 QQ SDK 授权令牌；openId 仅用于与反查结果比对，可省略")
public record QqLoginRequest(
        @Schema(description = "QQ SDK 授权得到的 accessToken", example = "8B0C0F1A2B3C...")
        @NotBlank(message = "accessToken 不能为空")
        String accessToken,
        @Schema(description = "客户端拿到的 openId，服务端会用 accessToken 反查比对；可省略", example = "A1B2C3D4E5F6...")
        String openId) {
}
