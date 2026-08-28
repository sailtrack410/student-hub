package com.studenthub.modules.jwauth.controller.dto;

import com.studenthub.integration.jw.KingosoftJwClient;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "教务个人信息")
public record JwProfileResponse(
        @Schema(description = "SSO 账号") String ssoAccount,
        @Schema(description = "教务姓名") String jwName,
        @Schema(description = "教务内部学号（教师为空）", nullable = true) String jwStudentNo,
        @Schema(description = "身份：STUDENT/TEACHER") String identityKind,
        @Schema(description = "年级（学生）", nullable = true) String grade,
        @Schema(description = "院(系)/部（学生）", nullable = true) String college,
        @Schema(description = "专业（学生）", nullable = true) String major,
        @Schema(description = "班级（学生）", nullable = true) String className) {

    public static JwProfileResponse from(KingosoftJwClient.JwProfile profile) {
        return new JwProfileResponse(profile.ssoAccount(), profile.jwName(), profile.jwStudentNo(),
                profile.identityKind(), profile.grade(), profile.college(), profile.major(), profile.className());
    }
}
