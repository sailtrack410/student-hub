package com.studenthub.modules.homework;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.homework.controller.dto.AdminHomeworkPageQuery;
import com.studenthub.modules.homework.controller.dto.AdminHomeworkResponse;
import com.studenthub.modules.homework.controller.dto.CreateHomeworkRequest;
import com.studenthub.modules.homework.controller.dto.HomeworkDetailResponse;
import com.studenthub.modules.homework.controller.dto.HomeworkPageQuery;
import com.studenthub.modules.homework.controller.dto.HomeworkSummaryResponse;
import com.studenthub.modules.homework.controller.dto.SubmitHomeworkRequest;
import com.studenthub.modules.homework.entity.HomeworkEntity;
import com.studenthub.modules.homework.entity.HomeworkSubmissionEntity;
import com.studenthub.modules.homework.mapper.HomeworkMapper;
import com.studenthub.modules.homework.mapper.HomeworkSubmissionMapper;
import com.studenthub.modules.homework.service.impl.HomeworkServiceImpl;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作业中心核心业务规则：截止时间校验、关闭校验、重复提交覆盖、校区隔离与关闭幂等。
 * 纯 Service 单测（Mock Mapper），不受 H2/MySQL 迁移环境影响。
 */
@ExtendWith(MockitoExtension.class)
class HomeworkServiceImplTest {

    @Mock
    private HomeworkMapper homeworkMapper;

    @Mock
    private HomeworkSubmissionMapper submissionMapper;

    @Mock
    private AdminCampusContextService campusContextService;

    @InjectMocks
    private HomeworkServiceImpl homeworkService;

    private static final AuthenticatedUser STUDENT = new AuthenticatedUser(
            1002L, 1L, 10L, "requester", "开发申请人", "STUDENT", "AUTHORIZED",
            List.of(), List.of(), null, null);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(campusContextService.currentCampusId()).thenReturn(1L);
    }

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(
            1001L, 1L, 10L, "admin", "开发管理员", "ADMIN", "AUTHORIZED",
            List.of(), List.of(), null, null);

    @Test
    void submitRejectsClosedHomework() {
        HomeworkEntity homework = homework(1L, 1L, HomeworkEntity.STATUS_CLOSED, LocalDateTime.now().plusDays(3));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.submit(STUDENT, 1L, new SubmitHomeworkRequest("我的答案")));

        assertEquals(ErrorCode.REQUEST_STATE_INVALID.getCode(), exception.getCode());
        verify(submissionMapper, never()).insert(any(HomeworkSubmissionEntity.class));
        verify(submissionMapper, never()).updateById(any(HomeworkSubmissionEntity.class));
    }

    @Test
    void submitRejectsOverdueHomework() {
        HomeworkEntity homework = homework(1L, 1L, HomeworkEntity.STATUS_PUBLISHED,
                LocalDateTime.now().minusMinutes(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.submit(STUDENT, 1L, new SubmitHomeworkRequest("我的答案")));

        assertEquals(ErrorCode.REQUEST_STATE_INVALID.getCode(), exception.getCode());
        verify(submissionMapper, never()).insert(any(HomeworkSubmissionEntity.class));
    }

    @Test
    void submitUpdatesExistingSubmissionInsteadOfInsertingDuplicate() {
        HomeworkEntity homework = homework(1L, 1L, HomeworkEntity.STATUS_PUBLISHED,
                LocalDateTime.now().plusDays(3));
        HomeworkSubmissionEntity existing = new HomeworkSubmissionEntity();
        existing.setId(200L);
        existing.setHomeworkId(1L);
        existing.setUserId(1002L);
        existing.setContent("第一次提交");
        existing.setStatus(HomeworkSubmissionEntity.STATUS_SUBMITTED);
        existing.setSubmittedAt(LocalDateTime.now().minusHours(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(submissionMapper.selectOne(any())).thenReturn(existing);

        HomeworkDetailResponse response = homeworkService.submit(
                STUDENT, 1L, new SubmitHomeworkRequest("  第二次提交  "));

        assertTrue(response.submitted());
        assertEquals("第二次提交", response.submissionContent());
        verify(submissionMapper).updateById(existing);
        verify(submissionMapper, never()).insert(any(HomeworkSubmissionEntity.class));
    }

    @Test
    void submitInsertsFirstSubmission() {
        HomeworkEntity homework = homework(1L, 1L, HomeworkEntity.STATUS_PUBLISHED,
                LocalDateTime.now().plusDays(3));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(submissionMapper.selectOne(any())).thenReturn(null);
        when(submissionMapper.insert(any(HomeworkSubmissionEntity.class))).thenReturn(1);

        HomeworkDetailResponse response = homeworkService.submit(
                STUDENT, 1L, new SubmitHomeworkRequest("第一次提交"));

        assertTrue(response.submitted());
        assertEquals("第一次提交", response.submissionContent());
        verify(submissionMapper).insert(any(HomeworkSubmissionEntity.class));
        verify(submissionMapper, never()).updateById(any(HomeworkSubmissionEntity.class));
    }

    @Test
    void submitRejectsHomeworkFromAnotherCampus() {
        HomeworkEntity homework = homework(1L, 2L, HomeworkEntity.STATUS_PUBLISHED,
                LocalDateTime.now().plusDays(3));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.submit(STUDENT, 1L, new SubmitHomeworkRequest("我的答案")));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(submissionMapper, never()).insert(any(HomeworkSubmissionEntity.class));
    }

    @Test
    void createRejectsDueAtBeforePublishAt() {
        CreateHomeworkRequest request = new CreateHomeworkRequest("软件工程导论", "第三章课后作业",
                "完成需求分析文档", null, LocalDateTime.now(), LocalDateTime.now().minusHours(1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.create(ADMIN, request));

        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
        verify(homeworkMapper, never()).insert(any(HomeworkEntity.class));
    }

    @Test
    void createUsesOperatorCampusAndDisplayNameAsFallbackTeacher() {
        LocalDateTime publishAt = LocalDateTime.now().minusHours(1);
        LocalDateTime dueAt = LocalDateTime.now().plusDays(7);
        CreateHomeworkRequest request = new CreateHomeworkRequest("软件工程导论", "第三章课后作业",
                "完成需求分析文档", "  ", publishAt, dueAt);

        AdminHomeworkResponse response = homeworkService.create(ADMIN, request);

        ArgumentCaptor<HomeworkEntity> captor = ArgumentCaptor.forClass(HomeworkEntity.class);
        verify(homeworkMapper).insert(captor.capture());
        HomeworkEntity saved = captor.getValue();
        assertEquals(1L, saved.getCampusId());
        assertEquals("开发管理员", saved.getTeacherName());
        assertEquals(HomeworkEntity.STATUS_PUBLISHED, saved.getStatus());
        assertEquals(publishAt, saved.getPublishAt());
        assertEquals(dueAt, saved.getDueAt());
        assertNotNull(response);
    }

    @Test
    void closeIsIdempotentForClosedHomework() {
        HomeworkEntity closed = homework(2L, 1L, HomeworkEntity.STATUS_CLOSED,
                LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(2L)).thenReturn(closed);

        AdminHomeworkResponse response = homeworkService.close(ADMIN, 2L);

        assertEquals(HomeworkEntity.STATUS_CLOSED, response.status());
        verify(homeworkMapper, never()).updateById(any(HomeworkEntity.class));
    }

    @Test
    void closeMarksPublishedHomeworkClosed() {
        HomeworkEntity published = homework(3L, 1L, HomeworkEntity.STATUS_PUBLISHED,
                LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(3L)).thenReturn(published);
        when(homeworkMapper.updateById(any(HomeworkEntity.class))).thenReturn(1);

        AdminHomeworkResponse response = homeworkService.close(ADMIN, 3L);

        assertEquals(HomeworkEntity.STATUS_CLOSED, response.status());
        ArgumentCaptor<HomeworkEntity> captor = ArgumentCaptor.forClass(HomeworkEntity.class);
        verify(homeworkMapper).updateById(captor.capture());
        assertEquals(HomeworkEntity.STATUS_CLOSED, captor.getValue().getStatus());
    }

    @Test
    void pageForAppReturnsEmptyWhenUserHasNoCampus() {
        AuthenticatedUser noCampus = new AuthenticatedUser(
                1003L, null, 10L, "worker", "开发维修员", "STUDENT", "AUTHORIZED",
                List.of(), List.of(), null, null);

        PageResult<HomeworkSummaryResponse> page = homeworkService.pageForApp(
                noCampus, new HomeworkPageQuery(1, 10));

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(homeworkMapper, never()).selectPage(any(), any());
    }

    @Test
    void pageForAdminRejectsUnknownStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> homeworkService.pageForAdmin(ADMIN, new AdminHomeworkPageQuery(1, 10, "DRAFT")));

        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
        verify(homeworkMapper, never()).selectPage(any(), any());
    }

    @Test
    void pageForAdminUsesValidatedStatusFilter() {
        when(homeworkMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10));

        PageResult<AdminHomeworkResponse> page = homeworkService.pageForAdmin(
                ADMIN, new AdminHomeworkPageQuery(1, 10, "published"));

        assertNotNull(page);
        assertFalse(page.getRecords().iterator().hasNext());
        verify(homeworkMapper).selectPage(any(), any());
    }

    private HomeworkEntity homework(Long id, Long campusId, String status, LocalDateTime dueAt) {
        HomeworkEntity entity = new HomeworkEntity();
        entity.setId(id);
        entity.setCampusId(campusId);
        entity.setCourseName("软件工程导论");
        entity.setTitle("第三章课后作业");
        entity.setContent("完成需求分析文档");
        entity.setTeacherName("张老师");
        entity.setPublishAt(LocalDateTime.now().minusDays(1));
        entity.setDueAt(dueAt);
        entity.setStatus(status);
        return entity;
    }
}
