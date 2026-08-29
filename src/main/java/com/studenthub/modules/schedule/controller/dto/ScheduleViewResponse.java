package com.studenthub.modules.schedule.controller.dto;

import java.time.LocalDate;
import java.util.List;

/** 当前学生课表视图：学期概览 + 课程列表 + 可切换学期 */
public record ScheduleViewResponse(
        String semesterCode,
        String semesterName,
        String className,
        List<ScheduleCourseItem> courses,
        List<SemesterOption> availableSemesters,
        /** 该学期第一周的周一日期（课表日期定位事实源，可为 null） */
        LocalDate firstMonday,
        /** 该学期总周数（可为 null） */
        Integer totalWeeks) {

    public record SemesterOption(String semesterCode, String semesterName) {
    }
}