package com.studenthub.modules.homework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.studenthub.modules.homework.service.HomeworkService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkServiceImpl implements HomeworkService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> ADMIN_STATUSES = Set.of(
            HomeworkEntity.STATUS_PUBLISHED, HomeworkEntity.STATUS_CLOSED);

    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final AdminCampusContextService campusContextService;

    @Override
    public PageResult<HomeworkSummaryResponse> pageForApp(AuthenticatedUser user, HomeworkPageQuery query) {
        int pageNum = normalizePageNum(query.pageNumOrDefault());
        int pageSize = normalizePageSize(query.pageSizeOrDefault());
        if (user.campusId() == null) {
            return PageResult.of(pageNum, pageSize, 0, List.of());
        }
        Page<HomeworkEntity> page = homeworkMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<HomeworkEntity>()
                        .eq(HomeworkEntity::getCampusId, user.campusId())
                        .eq(HomeworkEntity::getStatus, HomeworkEntity.STATUS_PUBLISHED)
                        .orderByDesc(HomeworkEntity::getPublishAt)
                        .orderByDesc(HomeworkEntity::getId));
        List<HomeworkEntity> records = page.getRecords();
        Map<Long, HomeworkSubmissionEntity> submissions = submissionsByHomework(records, user.userId());
        List<HomeworkSummaryResponse> rows = records.stream()
                .map(entity -> HomeworkSummaryResponse.from(entity, submissions.get(entity.getId())))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), rows);
    }

    @Override
    public HomeworkDetailResponse detailForApp(AuthenticatedUser user, Long homeworkId) {
        HomeworkEntity entity = requireHomeworkInCampus(user, homeworkId);
        return HomeworkDetailResponse.from(entity, findSubmission(homeworkId, user.userId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HomeworkDetailResponse submit(AuthenticatedUser user, Long homeworkId, SubmitHomeworkRequest request) {
        HomeworkEntity homework = requireHomeworkInCampus(user, homeworkId);
        if (!HomeworkEntity.STATUS_PUBLISHED.equals(homework.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "作业已关闭，无法提交");
        }
        LocalDateTime now = LocalDateTime.now();
        if (homework.getDueAt() != null && now.isAfter(homework.getDueAt())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "已过截止时间，无法提交");
        }
        String content = request.content().trim();
        HomeworkSubmissionEntity submission = findSubmission(homeworkId, user.userId());
        if (submission != null) {
            submission.setContent(content);
            submission.setSubmittedAt(now);
            submission.setStatus(HomeworkSubmissionEntity.STATUS_SUBMITTED);
            submissionMapper.updateById(submission);
        } else {
            HomeworkSubmissionEntity created = new HomeworkSubmissionEntity();
            created.setHomeworkId(homeworkId);
            created.setUserId(user.userId());
            created.setContent(content);
            created.setSubmittedAt(now);
            created.setStatus(HomeworkSubmissionEntity.STATUS_SUBMITTED);
            try {
                submissionMapper.insert(created);
            } catch (DuplicateKeyException exception) {
                // 并发重复提交：唯一键兜底，回退为更新已有记录
                log.info("作业提交并发冲突，回退为更新已有记录, homeworkId={}, userId={}", homeworkId, user.userId());
                HomeworkSubmissionEntity existing = findSubmission(homeworkId, user.userId());
                if (existing == null) {
                    throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "提交失败，请稍后重试");
                }
                existing.setContent(content);
                existing.setSubmittedAt(now);
                existing.setStatus(HomeworkSubmissionEntity.STATUS_SUBMITTED);
                submissionMapper.updateById(existing);
                submission = existing;
            }
            if (submission == null) {
                submission = created;
            }
        }
        return HomeworkDetailResponse.from(homework, submission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHomeworkResponse create(AuthenticatedUser operator, CreateHomeworkRequest request) {
        Long campusId = campusContextService.currentCampusId();
        LocalDateTime publishAt = request.publishAt() == null ? LocalDateTime.now() : request.publishAt();
        if (!request.dueAt().isAfter(publishAt)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "截止时间必须晚于发布时间");
        }
        HomeworkEntity entity = new HomeworkEntity();
        entity.setCampusId(campusId);
        entity.setCourseName(request.courseName().trim());
        entity.setTitle(request.title().trim());
        entity.setContent(request.content().trim());
        entity.setTeacherName(StringUtils.hasText(request.teacherName())
                ? request.teacherName().trim() : operator.displayName());
        entity.setPublishAt(publishAt);
        entity.setDueAt(request.dueAt());
        entity.setStatus(HomeworkEntity.STATUS_PUBLISHED);
        homeworkMapper.insert(entity);
        return AdminHomeworkResponse.from(entity);
    }

    @Override
    public PageResult<AdminHomeworkResponse> pageForAdmin(AuthenticatedUser operator, AdminHomeworkPageQuery query) {
        int pageNum = normalizePageNum(query.pageNumOrDefault());
        int pageSize = normalizePageSize(query.pageSizeOrDefault());
        String status = normalizeStatus(query.status());
        Page<HomeworkEntity> page = homeworkMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<HomeworkEntity>()
                        .eq(HomeworkEntity::getCampusId, campusContextService.currentCampusId())
                        .eq(status != null, HomeworkEntity::getStatus, status)
                        .orderByDesc(HomeworkEntity::getPublishAt)
                        .orderByDesc(HomeworkEntity::getId));
        List<AdminHomeworkResponse> rows = page.getRecords().stream()
                .map(AdminHomeworkResponse::from)
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHomeworkResponse close(AuthenticatedUser operator, Long homeworkId) {
        HomeworkEntity entity = requireHomeworkInAdminCampus(homeworkId);
        if (HomeworkEntity.STATUS_CLOSED.equals(entity.getStatus())) {
            return AdminHomeworkResponse.from(entity);
        }
        entity.setStatus(HomeworkEntity.STATUS_CLOSED);
        if (homeworkMapper.updateById(entity) == 0) {
            HomeworkEntity latest = homeworkMapper.selectById(homeworkId);
            if (latest == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
            }
            if (!HomeworkEntity.STATUS_CLOSED.equals(latest.getStatus())) {
                throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "作业状态已变化，请刷新后重试");
            }
            return AdminHomeworkResponse.from(latest);
        }
        return AdminHomeworkResponse.from(entity);
    }

    private HomeworkEntity requireHomeworkInAdminCampus(Long homeworkId) {
        HomeworkEntity entity = homeworkMapper.selectById(homeworkId);
        if (entity == null || !campusContextService.currentCampusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在或不属于当前校区");
        }
        return entity;
    }

    private HomeworkEntity requireHomeworkInCampus(AuthenticatedUser user, Long homeworkId) {
        HomeworkEntity entity = homeworkId == null ? null : homeworkMapper.selectById(homeworkId);
        if (entity == null || user.campusId() == null || !user.campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        return entity;
    }

    private HomeworkSubmissionEntity findSubmission(Long homeworkId, Long userId) {
        return submissionMapper.selectOne(new LambdaQueryWrapper<HomeworkSubmissionEntity>()
                .eq(HomeworkSubmissionEntity::getHomeworkId, homeworkId)
                .eq(HomeworkSubmissionEntity::getUserId, userId));
    }

    private Map<Long, HomeworkSubmissionEntity> submissionsByHomework(List<HomeworkEntity> records, Long userId) {
        List<Long> homeworkIds = records.stream().map(HomeworkEntity::getId).toList();
        if (homeworkIds.isEmpty()) {
            return Map.of();
        }
        return submissionMapper.selectList(new LambdaQueryWrapper<HomeworkSubmissionEntity>()
                        .eq(HomeworkSubmissionEntity::getUserId, userId)
                        .in(HomeworkSubmissionEntity::getHomeworkId, homeworkIds))
                .stream()
                .collect(Collectors.toMap(HomeworkSubmissionEntity::getHomeworkId, Function.identity(),
                        (first, second) -> first));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ADMIN_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "作业状态不合法，可选 PUBLISHED/CLOSED");
        }
        return normalized;
    }

    private int normalizePageNum(int pageNum) {
        return Math.max(pageNum, 1);
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }
}
