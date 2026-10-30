package com.studenthub.modules.systemconfig.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.systemconfig.controller.dto.ScheduledTaskResponse;
import com.studenthub.modules.systemconfig.controller.dto.ScheduledTaskRunResponse;
import com.studenthub.modules.systemconfig.service.ScheduledTaskRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/scheduled-tasks")
@Tag(name = "管理端-定时任务", description = "列出已注册的 @Scheduled 任务并支持手动执行一次")
@RequiredArgsConstructor
public class AdminScheduledTaskController {

    private final ScheduledTaskRegistry scheduledTaskRegistry;
    private final com.studenthub.modules.systemconfig.service.ScheduledTaskStateService scheduledTaskStateService;
    private final com.studenthub.modules.identity.security.CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "定时任务列表", description = "来自 Spring 调度器实际注册的任务；最近执行仅记录手动触发。",
            operationId = "adminListScheduledTasks")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:view') or hasAuthority('system:config:manage')")
    public Result<List<ScheduledTaskResponse>> list() {
        return Result.success(scheduledTaskRegistry.tasks());
    }

    @PostMapping("/toggle")
    @Operation(summary = "启停定时任务", description = "只控制 Spring 自动触发；手动执行仍可用于排障。状态写入 sys_scheduled_task_state，切换立即生效。",
            operationId = "adminToggleScheduledTask")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<ScheduledTaskResponse.ToggleResult> toggle(@RequestParam String key,
                                                             @RequestParam boolean enabled,
                                                             @RequestParam(required = false) String note) {
        var operator = currentUserProvider.requireCurrentUser();
        scheduledTaskStateService.setEnabled(key, enabled, operator.userId(), note);
        return Result.success(new ScheduledTaskResponse.ToggleResult(key, enabled));
    }

    @PostMapping("/run")
    @Operation(summary = "手动执行定时任务", description = "异步执行一次并返回 RUNNING，结果在列表中查看；同一任务并发执行会被拒绝。",
            operationId = "adminRunScheduledTask")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<ScheduledTaskRunResponse> run(@RequestParam String key) {
        return Result.success(scheduledTaskRegistry.run(key));
    }
}
