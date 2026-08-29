package com.studenthub.modules.schedule.controller.dto;

import java.util.List;

/** 课表导入结果 */
public record ScheduleImportResponse(
        String semesterCode,
        String semesterName,
        String className,
        int personalCount,
        int classCacheCount) {
}