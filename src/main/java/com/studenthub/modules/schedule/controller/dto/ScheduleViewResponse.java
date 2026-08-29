package com.studenthub.modules.schedule.controller.dto;

import java.util.List;

/** 当前学生课表视图：学期概览 + 课程列表 */
public record ScheduleViewResponse(
        String semesterCode,
        String semesterName,
        String className,
        List<ScheduleCourseItem> courses) {
}