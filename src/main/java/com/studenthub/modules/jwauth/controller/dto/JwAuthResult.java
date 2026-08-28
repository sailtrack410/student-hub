package com.studenthub.modules.jwauth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "教务认证结果")
public record JwAuthResult(
        @Schema(description = "固定为 AUTHENTICATED") String status,
        @Schema(description = "SSO 账号") String ssoAccount,
        @Schema(description = "教务姓名") String jwName,
        @Schema(description = "教务内部学号（教师为空）", nullable = true) String jwStudentNo,
        @Schema(description = "识别出的身份：STUDENT/TEACHER") String identityKind,
        @Schema(description = "平台身份是否因本次认证发生了切换") boolean identityChanged) {
}
