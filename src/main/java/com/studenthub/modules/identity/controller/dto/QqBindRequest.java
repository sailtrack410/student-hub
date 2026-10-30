package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "绑定 QQ 请求：accessToken 为已登录用户重新授权得到的 QQ SDK 令牌")
public record QqBindRequest(
        @Schema(description = "QQ SDK 授权得到的 accessToken", example = "8B0C0F1A2B3C...")
        @NotBlank(message = "accessToken 不能为空")
        String accessToken,
        @Schema(description = "客户端拿到的 openId，服务端会用 accessToken 反查比对；可省略", example = "A1B2C3D4E5F6...")
        String openId) {
}
