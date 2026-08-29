package com.studenthub.modules.schedule.controller.dto;

import com.studenthub.integration.jw.KingosoftScheduleClient;
import com.studenthub.integration.jw.KingosoftScheduleClient.JwCourseEntry;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;

/** 单条课表项（面向小程序） */
public record ScheduleCourseItem(
        String courseName,
        String teacher,
        String location,
        int dayOfWeek,
        int startSection,
        int endSection,
        String weeks,
        int weekParity) {

    public static ScheduleCourseItem from(ScheduleCourseEntity entity) {
        return new ScheduleCourseItem(
                entity.getCourseName(),
                entity.getTeacher(),
                entity.getLocation(),
                entity.getDayOfWeek(),
                entity.getStartSection(),
                entity.getEndSection(),
                entity.getWeeks(),
                entity.getWeekParity());
    }
}