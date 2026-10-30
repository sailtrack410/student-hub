package com.studenthub.modules.aichat;

import com.studenthub.modules.aichat.intent.AiAssistantDataService;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.jwauth.query.JwBindingQuery;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.entity.ScheduleSemesterEntity;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.schedule.mapper.ScheduleSemesterMapper;
import com.studenthub.modules.schedule.query.ScheduleQuery;
import com.studenthub.modules.servicecatalog.query.ServiceCatalogQuery;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.query.ServiceRequestQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiQueryBoundaryIntegrationTest {
    @Autowired AiAssistantDataService assistant;
    @Autowired ScheduleQuery scheduleQuery;
    @Autowired ServiceRequestQuery requestQuery;
    @Autowired ServiceCatalogQuery catalogQuery;
    @Autowired ServiceItemMapper itemMapper;
    @Autowired JwBindingQuery bindingQuery;
    @Autowired ScheduleCourseMapper courseMapper;
    @Autowired ScheduleSemesterMapper semesterMapper;
    @Autowired ServiceRequestMapper requestMapper;
    @Autowired JwBindingMapper bindingMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void users() {
        for (long id : new long[]{908002L, 908003L}) {
            jdbc.update("""
                    INSERT INTO sys_user (id, campus_id, default_organization_id, username, password_hash,
                        display_name, status, created_at, updated_at, created_by, updated_by, deleted, version)
                    VALUES (?, 1, 10, ?, 'query-test-no-login', '查询边界测试', 'ENABLED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0)
                    """, id, "query-boundary-" + id);
        }
    }

    @Test
    void scheduleProjectionPreservesOwnershipDateAndParityRules() {
        jdbc.update("DELETE FROM schedule_course WHERE user_id IN (908002,908003)");
        jdbc.update("DELETE FROM schedule_semester WHERE user_id IN (908002,908003)");
        ScheduleSemesterEntity semester = new ScheduleSemesterEntity();
        semester.setUserId(908002L);
        semester.setSemesterCode("S07-2026");
        semester.setSemesterName("查询边界测试学期");
        semester.setFirstMonday(LocalDate.of(2026, 10, 19));
        semester.setTotalWeeks(2);
        semesterMapper.insert(semester);
        course(908002L, "本人课程");
        course(908003L, "他人课程");

        ScheduleQuery.Snapshot snapshot = scheduleQuery.forUser(908002L);
        assertEquals(1, snapshot.courses().size());
        assertEquals("本人课程", snapshot.courses().get(0).courseName());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.courses().clear());
        assertTrue(assistant.resolveSchedule(908002L, LocalDate.of(2026, 10, 19), null)
                .contextText().contains("本人课程"));
        assertFalse(assistant.resolveSchedule(908002L, LocalDate.of(2026, 10, 19), null)
                .contextText().contains("他人课程"));
        assertTrue(assistant.resolveSchedule(908002L, LocalDate.of(2026, 10, 26), null)
                .contextText().contains("没有课"));
        assertTrue(assistant.resolveSchedule(908002L, LocalDate.of(2026, 11, 2), null)
                .contextText().contains("不在已导入的学期范围"));
        assertTrue(scheduleQuery.forUser(null).courses().isEmpty());
    }

    @Test
    void recentRequestsAreOwnedFilteredBoundedAndKeepAiActionLimit() {
        jdbc.update("DELETE FROM service_request WHERE requester_id = 908002");
        for (int i = 0; i < 25; i++) request(908002L, i, i % 2 == 0 ? "SUBMITTED" : "COMPLETED");
        request(908003L, 30, "PROCESSING");

        var all = requestQuery.recentForUser(908002L, ServiceRequestQuery.StatusFilter.ALL, Integer.MAX_VALUE);
        assertEquals(20, all.size());
        assertEquals("本人申请24", all.get(0).title());
        assertTrue(all.stream().noneMatch(r -> r.title().equals("本人申请30")));
        var active = requestQuery.recentForUser(908002L, ServiceRequestQuery.StatusFilter.IN_PROGRESS, 8);
        assertEquals(8, active.size());
        assertTrue(active.stream().allMatch(r -> r.status().equals("SUBMITTED")));
        assertTrue(requestQuery.recentForUser(908002L, ServiceRequestQuery.StatusFilter.COMPLETED, 8)
                .stream().allMatch(r -> r.status().equals("COMPLETED")));
        assertEquals(1, requestQuery.recentForUser(908002L, ServiceRequestQuery.StatusFilter.ALL, 0).size());
        assertTrue(requestQuery.recentForUser(null, ServiceRequestQuery.StatusFilter.ALL, 8).isEmpty());
        var resolved = assistant.resolveMyRequests(908002L, "IN_PROGRESS");
        assertTrue(resolved.contextText().contains("（8条）"));
        assertEquals(4, resolved.actions().size());
        assertEquals("REQUEST_LIST", resolved.actions().get(3).type());
    }

    @Test
    void catalogQueryEnforcesCampusAndEnabledState() {
        assertNotNull(catalogQuery.enabledItem("REPAIR-DORM", 1L));
        assertNull(catalogQuery.enabledItem("REPAIR-DORM", 99999L));
        assertNull(catalogQuery.enabledItem("REPAIR-DORM", null));
        assertEquals(1, assistant.resolveServiceItem("REPAIR-DORM", 1L).actions().size());
        ServiceItemEntity disabled = new ServiceItemEntity();
        disabled.setId(1001L);
        disabled.setStatus("DISABLED");
        itemMapper.updateById(disabled);
        assertNull(catalogQuery.enabledItem("REPAIR-DORM", 1L));
        assertTrue(assistant.resolveServiceItem("REPAIR-DORM", 1L).actions().isEmpty());
        assertEquals("SCHEDULE", assistant.resolveServiceItem("SCHEDULE-VIEW", 1L).actions().get(0).type());
    }

    @Test
    void bindingLookupOnlyPublishesLastSemesterCodeAndHonorsDeletion() {
        jdbc.update("DELETE FROM jw_binding WHERE user_id = 908002");
        JwBindingEntity binding = new JwBindingEntity();
        binding.setUserId(908002L);
        binding.setSsoAccount("query-boundary-908002");
        binding.setAuthStatus("AUTHENTICATED");
        binding.setLastAuthAt(LocalDateTime.now());
        binding.setLastSemesterCode("S07-2026");
        bindingMapper.insert(binding);
        assertEquals("S07-2026", bindingQuery.lastSemesterCode(908002L));
        assertNull(bindingQuery.lastSemesterCode(908003L));
        assertNull(bindingQuery.lastSemesterCode(null));
        bindingMapper.deleteById(binding.getId());
        assertNull(bindingQuery.lastSemesterCode(908002L));
    }

    private void course(Long userId, String name) {
        ScheduleCourseEntity c = new ScheduleCourseEntity();
        c.setUserId(userId);
        c.setSemesterCode("S07-2026");
        c.setSemesterName("查询边界测试学期");
        c.setCourseName(name);
        c.setDayOfWeek(1);
        c.setStartSection(1);
        c.setEndSection(2);
        c.setWeeks("1-2");
        c.setWeekParity(1);
        courseMapper.insert(c);
    }

    private void request(Long userId, int ordinal, String status) {
        ServiceRequestEntity r = new ServiceRequestEntity();
        r.setRequestNo("S07-" + userId + "-" + ordinal);
        r.setCampusId(1L);
        r.setItemId(1001L);
        r.setItemName("宿舍报修");
        r.setCategoryId(100L);
        r.setRequesterId(userId);
        r.setTitle("本人申请" + ordinal);
        r.setStatus(status);
        r.setSubmittedAt(LocalDateTime.of(2026, 10, 25, 12, 0).plusMinutes(ordinal));
        requestMapper.insert(r);
    }
}
