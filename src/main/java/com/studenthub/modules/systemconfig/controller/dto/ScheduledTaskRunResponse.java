package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "定时任务手动执行结果")
public record ScheduledTaskRunResponse(
        @Schema(description = "任务 key") String key,
        @Schema(description = "状态：RUNNING/SUCCESS/FAILED") String status,
        @Schema(description = "开始时间") String startedAt,
        @Schema(description = "结束时间，执行中为 null") String finishedAt,
        @Schema(description = "耗时毫秒，执行中为 null") Long durationMs,
        @Schema(description = "失败信息") String error) {
}
