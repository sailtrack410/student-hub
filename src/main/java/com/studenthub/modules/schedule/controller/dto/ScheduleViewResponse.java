package com.studenthub.modules.schedule.controller.dto;

import java.util.List;

/** 当前学生课表视图：学期概览 + 课程列表 + 可切换学期 */
public record ScheduleViewResponse(
        String semesterCode,
        String semesterName,
        String className,
        List<ScheduleCourseItem> courses,
        List<SemesterOption> availableSemesters) {

    public record SemesterOption(String semesterCode, String semesterName) {
    }
}