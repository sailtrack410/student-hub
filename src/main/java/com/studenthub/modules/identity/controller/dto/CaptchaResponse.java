package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录图形验证码")
public record CaptchaResponse(
        @Schema(description = "是否启用图形验证码；false 时其余字段为空") boolean enabled,
        @Schema(description = "验证码 ID，登录时原样回传") String captchaId,
        @Schema(description = "data:image/svg+xml;base64,... 格式的验证码图片") String image,
        @Schema(description = "有效期（秒）") Integer expiresInSeconds) {

    public static CaptchaResponse disabled() {
        return new CaptchaResponse(false, null, null, null);
    }
}
