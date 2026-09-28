package com.studenthub.modules.schedule.controller.dto;

import java.time.LocalDate;
import java.util.List;

/** 按周展开的课表视图：服务端把周次串解析成日期与时间段，客户端零解析 */
public record ScheduleWeekViewResponse(
        String semesterCode,
        String semesterName,
        /** 请求的周次（1 起） */
        int week,
        /** 该周周一日期 */
        LocalDate weekStart,
        /** 服务端推算的当前周次（日期不在学期范围内为 0） */
        int currentWeek,
        List<DayView> days) {

    /** 一天的课程（周一到周日固定 7 项顺序返回，无课时 courses 为空数组） */
    public record DayView(int dayOfWeek, LocalDate date, List<CourseView> courses) {
    }

    /** 展开后的单条课程：时间段由服务端节次映射生成 */
    public record CourseView(
            String courseName,
            String teacher,
            String location,
            int startSection,
            int endSection,
            String timeRange) {
    }
}
