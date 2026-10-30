package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminCommunityContentActionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportHandleRequest;
import com.studenthub.modules.community.controller.dto.CreateCommunityReportRequest;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.entity.CommunityReportEntity;
import com.studenthub.modules.community.entity.CommunityUserSanctionEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityDirectMessageMapper;
import com.studenthub.modules.community.mapper.CommunityModerationTemplateMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.mapper.CommunityAppealMapper;
import com.studenthub.modules.community.mapper.CommunityReportMapper;
import com.studenthub.modules.community.mapper.CommunityUserSanctionMapper;
import com.studenthub.modules.community.service.impl.CommunityModerationServiceImpl;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.security.RoleScope;
import com.studenthub.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityModerationServiceImplTest {

    @Mock
    private CommunityAppealMapper appealMapper;
    @Mock
    private CommunityReportMapper reportMapper;
    @Mock
    private CommunityUserSanctionMapper sanctionMapper;
    @Mock
    private CommunityPostMapper postMapper;
    @Mock
    private CommunityCommentMapper commentMapper;
    @Mock
    private CommunityDirectMessageMapper directMessageMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AdminCampusContextService campusContextService;
    @Mock
    private CommunityModerationTemplateMapper templateMapper;
    @Mock
    private FileStorageService fileStorageService;

    private CommunityModerationServiceImpl moderationService;

    private AuthenticatedUser reporter;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        moderationService = new CommunityModerationServiceImpl(appealMapper, reportMapper, sanctionMapper, postMapper,
                commentMapper, directMessageMapper, userMapper, notificationService, currentUserProvider,
                campusContextService, templateMapper, fileStorageService, new ObjectMapper());
        lenient().when(campusContextService.currentCampusId()).thenReturn(1L);
        reporter = new AuthenticatedUser(1002L, 1L, 10L, "reporter", "开发学生", "STUDENT", "BASIC",
                List.of(new RoleScope("REQUESTER", 10L, null)), List.of(), null, null);
        admin = new AuthenticatedUser(1L, 1L, 10L, "admin", "平台管理员", "ADMIN", "AUTHORIZED",
                List.of(new RoleScope("PLATFORM_ADMIN", 10L, null)), List.of("community:moderate"), null, null);
    }

    private CommunityPostEntity enabledPost(Long authorId) {
        CommunityPostEntity post = new CommunityPostEntity();
        post.setId(9001L);
        post.setCampusId(1L);
        post.setAuthorId(authorId);
        post.setTitle("低价刷单日结");
        post.setContent("兼职刷单日结 300，联系我");
        post.setStatus("ENABLED");
        return post;
    }

    private CommunityReportEntity pendingReport() {
        CommunityReportEntity report = new CommunityReportEntity();
        report.setId(500L);
        report.setCampusId(1L);
        report.setReporterId(1002L);
        report.setTargetType("POST");
        report.setTargetId(9001L);
        report.setTargetAuthorId(2002L);
        report.setStatus("PENDING");
        return report;
    }

    @Test
    void submitReportSavesSnapshotAndTargetAuthor() {
        when(postMapper.selectById(9001L)).thenReturn(enabledPost(2002L));
        when(reportMapper.selectCount(any())).thenReturn(0L);

        moderationService.submitReport(reporter,
                new CreateCommunityReportRequest("post", 9001L, "ad", "  明显是刷单广告  "));

        ArgumentCaptor<CommunityReportEntity> captor = ArgumentCaptor.forClass(CommunityReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        CommunityReportEntity saved = captor.getValue();
        assertEquals("PENDING", saved.getStatus());
        assertEquals("POST", saved.getTargetType());
        assertEquals("AD", saved.getReasonCode());
        assertEquals(2002L, saved.getTargetAuthorId());
        assertEquals(1002L, saved.getReporterId());
        assertEquals("  明显是刷单广告  ", saved.getReasonDetail());
        assertTrue(saved.getSnapshot().contains("兼职刷单日结 300"));
    }

    @Test
    void reportOwnContentRejected() {
        when(postMapper.selectById(9001L)).thenReturn(enabledPost(1002L));

        BusinessException exception = assertThrows(BusinessException.class, () -> moderationService
                .submitReport(reporter, new CreateCommunityReportRequest("POST", 9001L, "AD", null)));
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
        verify(reportMapper, never()).insert(any(CommunityReportEntity.class));
    }

    @Test
    void duplicatePendingReportRejected() {
        when(postMapper.selectById(9001L)).thenReturn(enabledPost(2002L));
        when(reportMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> moderationService
                .submitReport(reporter, new CreateCommunityReportRequest("POST", 9001L, "AD", null)));
        assertEquals(ErrorCode.COMMUNITY_REPORT_DUPLICATE.getCode(), exception.getCode());
        verify(reportMapper, never()).insert(any(CommunityReportEntity.class));
    }

    @Test
    void dailyReportLimitRejected() {
        when(postMapper.selectById(9001L)).thenReturn(enabledPost(2002L));
        when(reportMapper.selectCount(any())).thenReturn(0L, 20L);

        BusinessException exception = assertThrows(BusinessException.class, () -> moderationService
                .submitReport(reporter, new CreateCommunityReportRequest("POST", 9001L, "AD", null)));
        assertEquals(ErrorCode.COMMUNITY_REPORT_RATE_LIMITED.getCode(), exception.getCode());
        verify(reportMapper, never()).insert(any(CommunityReportEntity.class));
    }

    @Test
    void invalidReasonCodeRejected() {
        BusinessException exception = assertThrows(BusinessException.class, () -> moderationService
                .submitReport(reporter, new CreateCommunityReportRequest("POST", 9001L, "SOMETHING", null)));
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
    }

    @Test
    void acceptReportHidesContentAndSanctionsAuthor() {
        CommunityReportEntity report = pendingReport();
        CommunityPostEntity post = enabledPost(2002L);
        when(reportMapper.selectById(500L)).thenReturn(report);
        when(postMapper.selectById(9001L)).thenReturn(post);
        when(currentUserProvider.requireCurrentUser()).thenReturn(admin);
        when(sanctionMapper.selectOne(any())).thenReturn(null);

        moderationService.handleReport(500L,
                new AdminCommunityReportHandleRequest("ACCEPT", "HIDE", "MUTE", 72, "发布违规广告", null));

        assertEquals("HIDDEN", post.getStatus());
        assertEquals("ACCEPTED", report.getStatus());
        assertEquals(1L, report.getHandledBy());
        assertNotNull(report.getHandledAt());
        ArgumentCaptor<CommunityUserSanctionEntity> sanctionCaptor =
                ArgumentCaptor.forClass(CommunityUserSanctionEntity.class);
        verify(sanctionMapper).insert(sanctionCaptor.capture());
        CommunityUserSanctionEntity sanction = sanctionCaptor.getValue();
        assertEquals("MUTE", sanction.getSanctionType());
        assertEquals(2002L, sanction.getUserId());
        assertEquals(500L, sanction.getReportId());
        assertNotNull(sanction.getEndAt());
        verify(reportMapper).updateById(report);
        // 内容作者、被处罚人、举报人各一条通知
        verify(notificationService, times(3)).send(any());
    }

    @Test
    void acceptReportWithoutContentActionKeepsContentAndAppliesNoSanction() {
        CommunityReportEntity report = pendingReport();
        when(reportMapper.selectById(500L)).thenReturn(report);
        when(currentUserProvider.requireCurrentUser()).thenReturn(admin);

        moderationService.handleReport(500L,
                new AdminCommunityReportHandleRequest("ACCEPT", "NONE", null, null, "口头警告", null));

        assertEquals("ACCEPTED", report.getStatus());
        verify(postMapper, never()).updateById(any(CommunityPostEntity.class));
        verify(sanctionMapper, never()).insert(any(CommunityUserSanctionEntity.class));
        verify(notificationService).send(any());
    }

    @Test
    void rejectReportKeepsContentUntouchedAndNotifiesReporter() {
        CommunityReportEntity report = pendingReport();
        when(reportMapper.selectById(500L)).thenReturn(report);
        when(currentUserProvider.requireCurrentUser()).thenReturn(admin);

        moderationService.handleReport(500L,
                new AdminCommunityReportHandleRequest("REJECT", null, null, null, "未达到处理标准", null));

        assertEquals("REJECTED", report.getStatus());
        verify(postMapper, never()).updateById(any(CommunityPostEntity.class));
        verify(notificationService).send(any());
    }

    @Test
    void handledReportCannotBeHandledAgain() {
        CommunityReportEntity report = pendingReport();
        report.setStatus("ACCEPTED");
        when(reportMapper.selectById(500L)).thenReturn(report);

        BusinessException exception = assertThrows(BusinessException.class, () -> moderationService
                .handleReport(500L, new AdminCommunityReportHandleRequest("REJECT", null, null, null, null, null)));
        assertEquals(ErrorCode.COMMUNITY_REPORT_HANDLED.getCode(), exception.getCode());
        verify(reportMapper, never()).updateById(any(CommunityReportEntity.class));
    }

    @Test
    void hidingCommentDecrementsPostCommentCount() {
        CommunityCommentEntity comment = new CommunityCommentEntity();
        comment.setId(7001L);
        comment.setPostId(9001L);
        comment.setAuthorId(2002L);
        comment.setContent("刷单广告评论");
        comment.setStatus("ENABLED");
        when(commentMapper.selectById(7001L)).thenReturn(comment);
        CommunityPostEntity post = new CommunityPostEntity();
        post.setId(9001L);
        post.setAuthorId(2002L);
        post.setCommentCount(5);
        when(postMapper.selectById(9001L)).thenReturn(post);

        moderationService.hideContent("COMMENT", 7001L, new AdminCommunityContentActionRequest("广告"));

        assertEquals("HIDDEN", comment.getStatus());
        assertEquals(4, post.getCommentCount());
        verify(commentMapper).updateById(comment);
        verify(postMapper).updateById(post);
    }

    @Test
    void restoringCommentIncrementsPostCommentCount() {
        CommunityCommentEntity comment = new CommunityCommentEntity();
        comment.setId(7001L);
        comment.setPostId(9001L);
        comment.setAuthorId(2002L);
        comment.setContent("被误下架的评论");
        comment.setStatus("HIDDEN");
        when(commentMapper.selectById(7001L)).thenReturn(comment);
        CommunityPostEntity post = new CommunityPostEntity();
        post.setId(9001L);
        post.setAuthorId(2002L);
        post.setCommentCount(4);
        when(postMapper.selectById(9001L)).thenReturn(post);

        moderationService.restoreContent("COMMENT", 7001L, null);

        assertEquals("ENABLED", comment.getStatus());
        assertEquals(5, post.getCommentCount());
    }

    @Test
    void mutedUserIsBlockedFromPosting() {
        CommunityUserSanctionEntity sanction = new CommunityUserSanctionEntity();
        sanction.setId(8001L);
        sanction.setUserId(1002L);
        sanction.setSanctionType("MUTE");
        sanction.setStatus("ACTIVE");
        when(sanctionMapper.selectOne(any())).thenReturn(sanction);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> moderationService.requireNotMuted(1002L));
        assertEquals(ErrorCode.COMMUNITY_MUTED.getCode(), exception.getCode());
    }

    @Test
    void userWithoutSanctionCanPost() {
        when(sanctionMapper.selectOne(any())).thenReturn(null);

        moderationService.requireNotMuted(1002L);

        verify(notificationService, never()).send(any());
    }

    @Test
    void sanctioningAlreadySanctionedUserRejected() {
        CommunityUserSanctionEntity active = new CommunityUserSanctionEntity();
        active.setId(8001L);
        active.setSanctionType("MUTE");
        active.setStatus("ACTIVE");
        when(sanctionMapper.selectOne(any())).thenReturn(active);
        com.studenthub.modules.identity.entity.UserEntity target =
                new com.studenthub.modules.identity.entity.UserEntity();
        target.setId(2002L);
        target.setCampusId(1L);
        when(userMapper.selectById(2002L)).thenReturn(target);
        when(currentUserProvider.requireCurrentUser()).thenReturn(admin);

        BusinessException exception = assertThrows(BusinessException.class, () -> moderationService
                .sanctionUser(2002L, new com.studenthub.modules.community.controller.dto.AdminCommunitySanctionRequest(
                        "MUTE", 24, "重复处罚")));
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
        verify(sanctionMapper, never()).insert(any(CommunityUserSanctionEntity.class));
    }
}
