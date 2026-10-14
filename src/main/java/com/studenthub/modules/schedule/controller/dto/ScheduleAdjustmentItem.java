package com.studenthub.modules.schedule.controller.dto;

import com.studenthub.modules.schedule.entity.ScheduleAdjustmentEntity;

import java.util.List;

/** 调课通知列表项：不含 raw_text（原始 HTML 只留库内回溯用） */
public record ScheduleAdjustmentItem(
        Long id,
        String semesterCode,
        String noticeTime,
        String actionType,
        String courseCode,
        String courseName,
        String classNo,
        List<String> targetClasses,
        String summary) {

    public static ScheduleAdjustmentItem from(ScheduleAdjustmentEntity entity, List<String> targetClasses) {
        return new ScheduleAdjustmentItem(entity.getId(), entity.getSemesterCode(), entity.getNoticeTime(),
                entity.getActionType(), entity.getCourseCode(), entity.getCourseName(),
                entity.getClassNo(), targetClasses, entity.getSummary());
    }
}
