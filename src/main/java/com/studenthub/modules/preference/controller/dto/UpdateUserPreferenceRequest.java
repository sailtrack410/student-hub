package com.studenthub.modules.preference.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新用户偏好")
public record UpdateUserPreferenceRequest(
        @Schema(description = "私信推送") Boolean chatPush,
        @Schema(description = "报修推送") Boolean repairPush,
        @Schema(description = "工单推送") Boolean workOrderPush,
        @Schema(description = "私信提醒") Boolean chatReminder,
        @Schema(description = "公开学业信息") Boolean showAcademicInfo,
        @Schema(description = "展示勋章") Boolean showMedals) {
}
