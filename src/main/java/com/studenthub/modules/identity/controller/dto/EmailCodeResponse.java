package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "验证码发送结果；仅 dev/test 且未配置 SMTP（或显式允许）时携带 devCode，生产环境为 null")
public record EmailCodeResponse(String email, String devCode, String message) {
}
