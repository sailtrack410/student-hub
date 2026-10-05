package com.studenthub.modules.social.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 隐私设置读写（需求清单 2.4）。
 */
@Schema(description = "隐私设置")
public record PrivacySettingsResponse(
        @Schema(description = "向他人公开年级与专业（false 时他人主页学院/专业/班级置空）") boolean showAcademicInfo,
        @Schema(description = "公开我的勋章（false 时名片与主页不展示勋章）") boolean showMedals) {
}
