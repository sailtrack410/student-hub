package com.studenthub.modules.jwauth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "教务个人信息")
public record JwProfileResponse(
        @Schema(description = "SSO 账号") String ssoAccount,
        @Schema(description = "教务姓名") String jwName,
        @Schema(description = "教务内部学号（教师为空）", nullable = true) String jwStudentNo,
        @Schema(description = "身份：STUDENT/TEACHER") String identityKind) {
}
