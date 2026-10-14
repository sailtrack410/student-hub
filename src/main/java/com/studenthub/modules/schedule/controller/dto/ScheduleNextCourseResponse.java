package com.studenthub.modules.schedule.controller.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** 首页 BFF 用：当前/下一节有课信息；无课时 hasCourse=false 且相关字段为 null */
public record ScheduleNextCourseResponse(
        boolean hasCourse,
        /** 当前正在上的课；无当前节课程而为下一节时为 null */
        CourseView current,
        /** 下一节要上的课 */
        CourseView next,
        /** 供展示的当前周次（日期不在学期内为 0） */
        int week,
        LocalDate date) {

    public record CourseView(
            String courseName,
            String teacher,
            String location,
            int dayOfWeek,
            int startSection,
            int endSection,
            String timeRange) {
    }
}
