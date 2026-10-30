package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "定时任务")
public record ScheduledTaskResponse(
        @Schema(description = "任务 key：类名#方法名") String key,
        @Schema(description = "目标类") String className,
        @Schema(description = "目标方法") String methodName,
        @Schema(description = "触发规则：cron / fixedDelay / fixedRate") String trigger,
        @Schema(description = "任务说明") String description,
        @Schema(description = "自动触发是否启用；false 时调度仍注册但任务体直接返回") boolean enabled,
        @Schema(description = "是否正在执行（手动触发）") boolean running,
        @Schema(description = "最近一次手动执行状态：RUNNING/SUCCESS/FAILED") String lastStatus,
        @Schema(description = "最近一次手动执行开始时间") String lastStartedAt,
        @Schema(description = "最近一次手动执行结束时间") String lastFinishedAt,
        @Schema(description = "最近一次手动执行耗时毫秒") Long lastDurationMs,
        @Schema(description = "最近一次失败信息（截断）") String lastError) {

    @Schema(description = "定时任务启停结果")
    public record ToggleResult(String key, boolean enabled) {
    }
}
