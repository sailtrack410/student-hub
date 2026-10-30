package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 定时发布请求（C08）：到点由定时任务统一发布。 */
@Schema(description = "官方通知定时发布请求")
public record AdminOfficialFeedScheduleRequest(
        @Schema(description = "定时发布时间（必须晚于当前时间）") @NotNull @Future LocalDateTime scheduledAt) {
}
