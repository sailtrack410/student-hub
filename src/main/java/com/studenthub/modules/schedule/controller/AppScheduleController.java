package com.studenthub.modules.schedule.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;
import com.studenthub.modules.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/schedule")
@Tag(name = "小程序-课表")
@RequiredArgsConstructor
public class AppScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/import")
    @Operation(summary = "导入课表", description = "从教务系统拉取当前学生个人课表并保存，同时刷新班级共享缓存。",
            operationId = "importSchedule")
    public Result<ScheduleImportResponse> importSchedule() {
        return Result.success(scheduleService.importSchedule(currentUserProvider.requireCurrentUser()));
    }

    @GetMapping("/view")
    @Operation(summary = "查询课表", description = "返回当前学生已导入的个人课表。", operationId = "getScheduleView")
    public Result<ScheduleViewResponse> view() {
        return Result.success(scheduleService.view(currentUserProvider.requireCurrentUser()));
    }
}