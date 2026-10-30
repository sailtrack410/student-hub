package com.studenthub.modules.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.integration.jw.KingosoftScheduleClient;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import com.studenthub.modules.schedule.controller.dto.ScheduleWeekViewResponse;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleSemesterEntity;
import com.studenthub.modules.schedule.mapper.ScheduleAdjustmentMapper;
import com.studenthub.modules.schedule.mapper.ScheduleClassCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleSemesterMapper;
import com.studenthub.modules.schedule.service.impl.ScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplTest {

    @Mock JwBindingMapper bindingMapper;
    @Mock JwSessionStore sessionStore;
    @Mock KingosoftScheduleClient scheduleClient;
    @Mock ScheduleCourseMapper courseMapper;
    @Mock ScheduleSemesterMapper semesterMapper;
    @Mock ScheduleClassCourseMapper classCourseMapper;
    @Mock ScheduleAdjustmentMapper adjustmentMapper;
    @Mock TransactionTemplate transactionTemplate;

    ScheduleServiceImpl service;
    AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(1002L, null, null, "student", "测试学生",
                "STUDENT", "AUTHORIZED", List.of(), List.of(), null, null);
        service = new ScheduleServiceImpl(bindingMapper, sessionStore, scheduleClient,
                courseMapper, semesterMapper, classCourseMapper, adjustmentMapper,
                transactionTemplate, new ObjectMapper());
        when(bindingMapper.findByUserId(1002L)).thenReturn(new JwBindingEntity());
        when(semesterMapper.selectByUserId(1002L)).thenReturn(List.of());
    }

    @Test
    void weekViewFiltersWeeksAndUsesSourceTimes() {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        int currentWeek = com.studenthub.modules.schedule.calendar.SemesterCalendar.weekOf(
                LocalDate.now(), monday);
        ScheduleSemesterEntity semester = new ScheduleSemesterEntity();
        semester.setSemesterCode("2026,0");
        semester.setSemesterName("2026-2027学年第一学期");
        semester.setFirstMonday(monday);
        semester.setTotalWeeks(25);
        when(semesterMapper.selectByUserId(1002L)).thenReturn(List.of(semester));
        when(bindingMapper.findByUserId(1002L)).thenReturn(binding("2026,0"));
        when(courseMapper.selectByUserId(1002L)).thenReturn(List.of(
                course("早期课程", "1-3", 7, "16:05-16:50"),
                course("本周课程", Integer.toString(currentWeek), 7, "16:05-16:50"),
                course("其他周课程", currentWeek == 1 ? "2" : "1", 8, "16:55-17:40"),
                course("晚课", Integer.toString(currentWeek), 12, "21:00-21:45")));

        ScheduleWeekViewResponse result = service.weekView(user, null, currentWeek);

        assertEquals(currentWeek, result.week());
        assertEquals(monday, result.weekStart());
        assertEquals(3, result.days().get(0).courses().size());
        assertTrue(result.days().get(0).courses().stream()
                .noneMatch(course -> course.courseName().equals("其他周课程")));
        assertEquals("21:00-21:45", result.days().get(0).courses().get(2).timeRange());
    }

    @Test
    void rejectsOutOfRangeInsteadOfReturningPreviousWeek() {
        when(bindingMapper.findByUserId(1002L)).thenReturn(binding("2026,0"));
        ScheduleSemesterEntity semester = new ScheduleSemesterEntity();
        semester.setSemesterCode("2026,0");
        semester.setTotalWeeks(25);
        when(semesterMapper.selectByUserId(1002L)).thenReturn(List.of(semester));
        when(courseMapper.selectByUserId(1002L)).thenReturn(List.of());

        assertThrows(com.studenthub.exception.BusinessException.class,
                () -> service.weekView(user, null, 26));
    }

    private JwBindingEntity binding(String semester) {
        JwBindingEntity binding = new JwBindingEntity();
        binding.setLastSemesterCode(semester);
        return binding;
    }

    private ScheduleCourseEntity course(String name, String weeks, int section, String time) {
        ScheduleCourseEntity course = new ScheduleCourseEntity();
        course.setSemesterCode("2026,0");
        course.setCourseName(name);
        course.setTeacher("测试教师");
        course.setLocation("测试教室");
        course.setDayOfWeek(1);
        course.setStartSection(section);
        course.setEndSection(section);
        course.setWeeks(weeks);
        course.setWeekParity(0);
        course.setTimeRange(time);
        return course;
    }
}
