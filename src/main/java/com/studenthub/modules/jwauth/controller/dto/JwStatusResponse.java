package com.studenthub.modules.jwauth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "教务认证状态响应")
public record JwStatusResponse(
        @Schema(description = "状态：UNBOUND 未认证 / AUTHENTICATED 已认证 / EXPIRED 已失效") String status,
        @Schema(description = "SSO 账号", nullable = true) String ssoAccount,
        @Schema(description = "教务姓名", nullable = true) String jwName,
        @Schema(description = "身份：STUDENT/TEACHER", nullable = true) String identityKind,
        @Schema(description = "年级（学生）", nullable = true) String grade,
        @Schema(description = "院(系)/部（学生）", nullable = true) String college,
        @Schema(description = "专业（学生）", nullable = true) String major,
        @Schema(description = "班级（学生）", nullable = true) String className,
        @Schema(description = "最后认证时间", nullable = true) LocalDateTime lastAuthAt,
        @Schema(description = "会话到期时间", nullable = true) LocalDateTime sessionExpiresAt) {

    public static final String UNBOUND = "UNBOUND";
    public static final String AUTHENTICATED = "AUTHENTICATED";
    public static final String EXPIRED = "EXPIRED";
}
