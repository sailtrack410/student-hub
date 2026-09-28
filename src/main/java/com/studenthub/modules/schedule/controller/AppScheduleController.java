package com.studenthub.modules.schedule.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.schedule.controller.dto.ScheduleAdjustmentItem;
import com.studenthub.modules.schedule.controller.dto.ScheduleImportResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleNextCourseResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleViewResponse;
import com.studenthub.modules.schedule.controller.dto.ScheduleWeekViewResponse;
import com.studenthub.modules.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/schedule")
@Tag(name = "小程序-课表", description = "课表导入与查看")
@RequiredArgsConstructor
public class AppScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/import")
    @Operation(summary = "导入课表", description = "从教务系统拉取当前学生个人课表并保存；传 semesterCode 则导入指定学期。",
            operationId = "importSchedule")
    public Result<ScheduleImportResponse> importSchedule(
            @RequestParam(required = false) String semesterCode) {
        return Result.success(scheduleService.importSchedule(currentUserProvider.requireCurrentUser(), semesterCode));
    }

    @GetMapping("/view")
    @Operation(summary = "查询课表", description = "返回当前学生已导入的个人课表；不传 semesterCode 时返回最近同步学期。",
            operationId = "getScheduleView")
    public Result<ScheduleViewResponse> view(
            @RequestParam(required = false) String semesterCode) {
        return Result.success(scheduleService.view(currentUserProvider.requireCurrentUser(), semesterCode));
    }

    @GetMapping("/view-week")
    @Operation(summary = "按周查询课表", description = "服务端展开指定周：days[] 含日期与时间段（节次映射服务端统一维护），"
            + "客户端不再解析周次串；week 缺省返回当前周。个人课表缺失时回落班级课表缓存。",
            operationId = "getScheduleWeekView")
    public Result<ScheduleWeekViewResponse> weekView(
            @RequestParam(required = false) String semesterCode,
            @RequestParam(required = false) Integer week) {
        return Result.success(scheduleService.weekView(
                currentUserProvider.requireCurrentUser(), semesterCode, week));
    }

    @GetMapping("/next-course")
    @Operation(summary = "当前/下一节课程", description = "首页 BFF：返回当前正在上的课与下一节课；无课时 hasCourse=false。",
            operationId = "getNextCourse")
    public Result<ScheduleNextCourseResponse> nextCourse() {
        return Result.success(scheduleService.nextCourse(currentUserProvider.requireCurrentUser()));
    }

    @GetMapping("/adjustments")
    @Operation(summary = "调课通知列表", description = "返回指定学期（缺省为最新有公告学期）的调课/补课/停课公告，按发布时间倒序。",
            operationId = "getScheduleAdjustments")
    public Result<List<ScheduleAdjustmentItem>> adjustments(
            @RequestParam(required = false) String semesterCode) {
        return Result.success(scheduleService.adjustments(semesterCode));
    }
}