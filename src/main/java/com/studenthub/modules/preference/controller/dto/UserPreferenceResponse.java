package com.studenthub.modules.preference.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "用户偏好")
public record UserPreferenceResponse(
        boolean chatPush,
        boolean repairPush,
        boolean workOrderPush,
        boolean chatReminder,
        boolean showAcademicInfo,
        boolean showMedals,
        LocalDateTime updatedAt) {
}
