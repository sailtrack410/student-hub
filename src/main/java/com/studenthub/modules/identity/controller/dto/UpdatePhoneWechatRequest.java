package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信手机号绑定请求")
public record UpdatePhoneWechatRequest(
        @Schema(description = "button open-type=getPhoneNumber 返回的 code") @NotBlank String code) {
}
