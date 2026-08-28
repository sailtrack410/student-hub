package com.studenthub.modules.jwauth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "教务验证码响应")
public record JwCaptchaResponse(
        @Schema(description = "验证码 uid，认证时原样传回") String uid,
        @Schema(description = "验证码图片 base64（PNG）") String imageBase64) {
}
