package com.studenthub.modules.identity.controller.dto;

import com.studenthub.common.util.PhoneNumbers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "手动填写手机号请求")
public record UpdatePhoneRequest(
        @Schema(description = "手机号") @NotBlank @Pattern(regexp = PhoneNumbers.PATTERN, message = "手机号格式不正确") String phone) {
}
