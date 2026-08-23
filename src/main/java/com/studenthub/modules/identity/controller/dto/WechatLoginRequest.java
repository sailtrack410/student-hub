package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信小程序登录请求")
public record WechatLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空")
        @Schema(description = "微信 wx.login() 返回的一次性登录凭证，前端无需提交其他字段",
                example = "wx-code-example", requiredMode = Schema.RequiredMode.REQUIRED)
        String code) {
}
