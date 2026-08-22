package com.studenthub.modules.identity.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空") String code) {
}
