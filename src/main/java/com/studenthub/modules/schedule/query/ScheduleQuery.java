package com.studenthub.modules.schedule.query;

import java.time.LocalDate;
import java.util.List;

/** 课表领域拥有查询和学期快照，调用方不依赖持久化实体。 */
public interface ScheduleQuery {
    Snapshot forUser(Long userId);

    record Snapshot(List<Course> courses, List<Semester> semesters) {
        public Snapshot {
            courses = List.copyOf(courses);
            semesters = List.copyOf(semesters);
        }
    }

    record Course(String semesterCode, String semesterName, Integer dayOfWeek,
                  Integer startSection, Integer endSection, String weeks, Integer weekParity,
                  String courseName, String teacher, String location) {}

    record Semester(String semesterCode, String semesterName, LocalDate firstMonday, Integer totalWeeks) {}
}
