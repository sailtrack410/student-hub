package com.studenthub.modules.schedule.query;

import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleSemesterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleQueryService implements ScheduleQuery {
    private final ScheduleCourseMapper courseMapper;
    private final ScheduleSemesterMapper semesterMapper;

    @Override
    public Snapshot forUser(Long userId) {
        if (userId == null) return new Snapshot(List.of(), List.of());
        List<Course> courses = courseMapper.selectByUserId(userId).stream()
                .map(c -> new Course(c.getSemesterCode(), c.getSemesterName(), c.getDayOfWeek(),
                        c.getStartSection(), c.getEndSection(), c.getWeeks(), c.getWeekParity(),
                        c.getCourseName(), c.getTeacher(), c.getLocation()))
                .toList();
        List<Semester> semesters = semesterMapper.selectByUserId(userId).stream()
                .map(s -> new Semester(s.getSemesterCode(), s.getSemesterName(), s.getFirstMonday(), s.getTotalWeeks()))
                .toList();
        return new Snapshot(courses, semesters);
    }
}
