package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminCommunityContentActionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportPageQuery;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportSummaryResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunitySanctionRequest;
import com.studenthub.modules.community.controller.dto.CreateCommunityReportRequest;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.entity.CommunityReportEntity;
import com.studenthub.modules.community.entity.CommunityUserSanctionEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityDirectMessageMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.mapper.CommunityReportMapper;
import com.studenthub.modules.community.mapper.CommunityUserSanctionMapper;
import com.studenthub.modules.community.service.CommunityModerationService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区内容治理实现。
 * 举报在提交时保存内容快照，作者随后删改也能追溯；处置动作统一走状态迁移方法，
 * 保证内容可见性与帖子评论计数一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityModerationServiceImpl implements CommunityModerationService {

    private static final String ENABLED = "ENABLED";
    private static final String HIDDEN = "HIDDEN";
    private static final String DELETED = "DELETED";
    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";
    private static final String MUTE = "MUTE";
    private static final String BAN = "BAN";
    private static final String TARGET_POST = "POST";
    private static final String TARGET_COMMENT = "COMMENT";
    private static final String ACTION_ACCEPT = "ACCEPT";
    private static final String ACTION_REJECT = "REJECT";
    private static final String CONTENT_NONE = "NONE";
    private static final String CONTENT_HIDE = "HIDE";
    private static final String CONTENT_DELETE = "DELETE";
    private static final String NOTIFICATION_TYPE = "COMMUNITY_MODERATION";
    private static final Set<String> TARGET_TYPES = Set.of(TARGET_POST, TARGET_COMMENT, "MESSAGE", "USER");
    private static final Set<String> REASON_CODES = Set.of("AD", "ABUSE", "PORN", "POLITICS", "PRIVACY", "OTHER");
    private static final Set<String> SANCTION_TYPES = Set.of(MUTE, BAN);
    private static final Set<String> CONTENT_ACTIONS = Set.of(CONTENT_NONE, CONTENT_HIDE, CONTENT_DELETE);
    /** 单个用户每日举报上限，防止举报功能被滥用 */
    private static final int DAILY_REPORT_LIMIT = 20;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CommunityReportMapper reportMapper;
    private final CommunityUserSanctionMapper sanctionMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityDirectMessageMapper directMessageMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReport(AuthenticatedUser user, CreateCommunityReportRequest request) {
        String targetType = normalize(request.targetType());
        String reasonCode = normalize(request.reasonCode());
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "举报对象类型不合法");
        }
        if (!REASON_CODES.contains(reasonCode)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "举报原因不合法");
        }
        ReportSnapshot snapshot = resolveSnapshot(user, targetType, request.targetId());
        if (snapshot.authorId() != null && snapshot.authorId().equals(user.userId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不能举报自己发布的内容");
        }
        Long pending = reportMapper.selectCount(new LambdaQueryWrapper<CommunityReportEntity>()
                .eq(CommunityReportEntity::getReporterId, user.userId())
                .eq(CommunityReportEntity::getTargetType, targetType)
                .eq(CommunityReportEntity::getTargetId, request.targetId())
                .eq(CommunityReportEntity::getStatus, PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_DUPLICATE);
        }
        Long todayCount = reportMapper.selectCount(new LambdaQueryWrapper<CommunityReportEntity>()
                .eq(CommunityReportEntity::getReporterId, user.userId())
                .ge(CommunityReportEntity::getCreatedAt, LocalDate.now(ZONE).atStartOfDay()));
        if (todayCount != null && todayCount >= DAILY_REPORT_LIMIT) {
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_RATE_LIMITED);
        }
        CommunityReportEntity entity = new CommunityReportEntity();
        entity.setCampusId(user.campusId());
        entity.setReporterId(user.userId());
        entity.setTargetType(targetType);
        entity.setTargetId(request.targetId());
        entity.setTargetAuthorId(snapshot.authorId());
        entity.setReasonCode(reasonCode);
        entity.setReasonDetail(request.reasonDetail());
        entity.setSnapshot(writeJson(snapshot.payload()));
        entity.setStatus(PENDING);
        reportMapper.insert(entity);
        log.info("社区举报已提交, reportId={}, reporterId={}, target={}#{}, reason={}",
                entity.getId(), user.userId(), targetType, request.targetId(), reasonCode);
    }

    @Override
    public PageResult<AdminCommunityReportResponse> pageReports(AdminCommunityReportPageQuery query) {
        LambdaQueryWrapper<CommunityReportEntity> wrapper = new LambdaQueryWrapper<CommunityReportEntity>()
                .eq(isNotBlank(query.status()), CommunityReportEntity::getStatus, normalize(query.status()))
                .eq(isNotBlank(query.targetType()), CommunityReportEntity::getTargetType, normalize(query.targetType()))
                .orderByAsc(CommunityReportEntity::getStatus)
                .orderByDesc(CommunityReportEntity::getCreatedAt);
        Page<CommunityReportEntity> page = reportMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        List<AdminCommunityReportResponse> records = page.getRecords().stream().map(this::toReportResponse).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public AdminCommunityReportSummaryResponse summary() {
        long pendingCount = countReports(PENDING);
        long acceptedCount = countReports(ACCEPTED);
        long rejectedCount = countReports(REJECTED);
        long todayCount = reportMapper.selectCount(new LambdaQueryWrapper<CommunityReportEntity>()
                .ge(CommunityReportEntity::getCreatedAt, LocalDate.now(ZONE).atStartOfDay()));
        long activeSanctionCount = sanctionMapper.selectCount(new LambdaQueryWrapper<CommunityUserSanctionEntity>()
                .eq(CommunityUserSanctionEntity::getStatus, ACTIVE)
                .and(wrapper -> wrapper.isNull(CommunityUserSanctionEntity::getEndAt)
                        .or().gt(CommunityUserSanctionEntity::getEndAt, LocalDateTime.now(ZONE))));
        return new AdminCommunityReportSummaryResponse(pendingCount, acceptedCount, rejectedCount,
                todayCount, activeSanctionCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, AdminCommunityReportHandleRequest request) {
        CommunityReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_NOT_FOUND);
        }
        if (!PENDING.equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.COMMUNITY_REPORT_HANDLED);
        }
        String action = normalize(request.action());
        if (!ACTION_ACCEPT.equals(action) && !ACTION_REJECT.equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "处理结论只能是 ACCEPT 或 REJECT");
        }
        Long adminId = currentUserProvider.requireCurrentUser().userId();
        if (ACTION_ACCEPT.equals(action)) {
            applyContentAction(report, request.contentAction(), request.note());
            if (isNotBlank(request.sanctionType())) {
                String sanctionType = normalize(request.sanctionType());
                if (!SANCTION_TYPES.contains(sanctionType)) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "处罚类型不合法");
                }
                if (report.getTargetAuthorId() != null) {
                    createSanction(report.getCampusId(), report.getTargetAuthorId(), sanctionType,
                            request.sanctionHours(), request.note(), report.getId(), adminId);
                }
            }
        }
        report.setStatus(ACTION_ACCEPT.equals(action) ? ACCEPTED : REJECTED);
        report.setHandledBy(adminId);
        report.setHandledAt(LocalDateTime.now(ZONE));
        report.setHandleNote(request.note());
        reportMapper.updateById(report);

        // 处理结果反馈给举报人，举报才有正反馈闭环
        String feedback = ACTION_ACCEPT.equals(action) ? "你的举报已核实，我们已处理相关内容，感谢你共同维护社区"
                : "你的举报经核实未达到处理标准，如仍有疑问可联系校园服务台";
        if (isNotBlank(request.note())) {
            feedback = feedback + "（处理说明：" + request.note() + "）";
        }
        notify(report.getReporterId(), "举报处理结果", feedback, report.getId());
        log.info("社区举报已处理, reportId={}, action={}, handledBy={}", reportId, action, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideContent(String targetType, Long targetId, AdminCommunityContentActionRequest request) {
        applyStatusChange(targetType, targetId, HIDDEN, request == null ? null : request.reason(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreContent(String targetType, Long targetId, AdminCommunityContentActionRequest request) {
        applyStatusChange(targetType, targetId, ENABLED, request == null ? null : request.reason(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sanctionUser(Long userId, AdminCommunitySanctionRequest request) {
        String sanctionType = normalize(request.sanctionType());
        if (!SANCTION_TYPES.contains(sanctionType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "处罚类型不合法");
        }
        UserEntity target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Long adminId = currentUserProvider.requireCurrentUser().userId();
        Long campusId = target.getCampusId() == null
                ? currentUserProvider.requireCurrentUser().campusId() : target.getCampusId();
        createSanction(campusId, userId, sanctionType, request.hours(), request.reason(), null, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeSanction(Long sanctionId) {
        CommunityUserSanctionEntity sanction = sanctionMapper.selectById(sanctionId);
        if (sanction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处罚记录不存在");
        }
        sanction.setStatus(REVOKED);
        sanctionMapper.updateById(sanction);
        notify(sanction.getUserId(), "社区处罚已解除", "你的社区使用限制已解除，请遵守社区规范", null);
        log.info("社区处罚已撤销, sanctionId={}, userId={}, operator={}",
                sanctionId, sanction.getUserId(), currentUserProvider.requireCurrentUser().userId());
    }

    @Override
    public void requireNotMuted(Long userId) {
        CommunityUserSanctionEntity sanction = findActiveSanction(userId);
        if (sanction == null) {
            return;
        }
        String message = BAN.equals(sanction.getSanctionType())
                ? "你已被禁止访问社区，如有疑问可联系校园服务台" : "你已被限制发言，暂时无法发布内容";
        throw new BusinessException(ErrorCode.COMMUNITY_MUTED, message);
    }

    private CommunityUserSanctionEntity findActiveSanction(Long userId) {
        return sanctionMapper.selectOne(new LambdaQueryWrapper<CommunityUserSanctionEntity>()
                .eq(CommunityUserSanctionEntity::getUserId, userId)
                .eq(CommunityUserSanctionEntity::getStatus, ACTIVE)
                .in(CommunityUserSanctionEntity::getSanctionType, List.of(MUTE, BAN))
                .and(wrapper -> wrapper.isNull(CommunityUserSanctionEntity::getEndAt)
                        .or().gt(CommunityUserSanctionEntity::getEndAt, LocalDateTime.now(ZONE)))
                .orderByDesc(CommunityUserSanctionEntity::getCreatedAt)
                .last("LIMIT 1"));
    }

    private void createSanction(Long campusId, Long userId, String sanctionType, Integer hours,
                                String reason, Long reportId, Long operatorId) {
        CommunityUserSanctionEntity existing = findActiveSanction(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该用户已有生效中的处罚，请先撤销或等待到期");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        CommunityUserSanctionEntity entity = new CommunityUserSanctionEntity();
        entity.setCampusId(campusId);
        entity.setUserId(userId);
        entity.setSanctionType(sanctionType);
        entity.setReason(reason);
        entity.setReportId(reportId);
        entity.setStartAt(now);
        entity.setEndAt(hours == null || hours <= 0 ? null : now.plusHours(hours));
        entity.setStatus(ACTIVE);
        sanctionMapper.insert(entity);

        String title = BAN.equals(sanctionType) ? "社区使用限制通知" : "发言限制通知";
        String duration = entity.getEndAt() == null ? "长期有效" : "有效期至 " + entity.getEndAt().toLocalDate();
        String content = (BAN.equals(sanctionType) ? "你已被禁止访问社区，" : "你已被限制发言，")
                + duration + (isNotBlank(reason) ? "；原因：" + reason : "");
        notify(userId, title, content, reportId);
        log.info("社区处罚已生效, sanctionId={}, userId={}, type={}, endAt={}, operator={}",
                entity.getId(), userId, sanctionType, entity.getEndAt(), operatorId);
    }

    private void applyContentAction(CommunityReportEntity report, String contentAction, String note) {
        String action = isNotBlank(contentAction) ? normalize(contentAction) : CONTENT_NONE;
        if (!CONTENT_ACTIONS.contains(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "内容处置方式不合法");
        }
        if (CONTENT_NONE.equals(action) || !isContentTarget(report.getTargetType())) {
            return;
        }
        String status = CONTENT_HIDE.equals(action) ? HIDDEN : DELETED;
        String reason = isNotBlank(note) ? note : "内容违反社区规范";
        applyStatusChange(report.getTargetType(), report.getTargetId(), status, reason, true);
    }

    /** 内容状态迁移：同步维护帖子评论计数，导致评论可见性变化的场景不会漏计数 */
    private void applyStatusChange(String targetType, Long targetId, String newStatus, String reason, boolean notifyAuthor) {
        String type = normalize(targetType);
        if (TARGET_POST.equals(type)) {
            CommunityPostEntity post = postMapper.selectById(targetId);
            if (post == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
            }
            if (newStatus.equals(post.getStatus())) {
                return;
            }
            post.setStatus(newStatus);
            postMapper.updateById(post);
            if (notifyAuthor) {
                notify(post.getAuthorId(), "帖子内容处理通知",
                        "你的帖子「" + post.getTitle() + "」因" + reason + "已被" + statusLabel(newStatus), post.getId());
            }
            return;
        }
        if (TARGET_COMMENT.equals(type)) {
            CommunityCommentEntity comment = commentMapper.selectById(targetId);
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
            }
            if (newStatus.equals(comment.getStatus())) {
                return;
            }
            boolean wasVisible = ENABLED.equals(comment.getStatus());
            boolean willBeVisible = ENABLED.equals(newStatus);
            comment.setStatus(newStatus);
            commentMapper.updateById(comment);
            if (wasVisible != willBeVisible) {
                CommunityPostEntity post = postMapper.selectById(comment.getPostId());
                if (post != null) {
                    int current = post.getCommentCount() == null ? 0 : post.getCommentCount();
                    post.setCommentCount(willBeVisible ? current + 1 : Math.max(0, current - 1));
                    postMapper.updateById(post);
                }
            }
            if (notifyAuthor) {
                notify(comment.getAuthorId(), "评论内容处理通知",
                        "你的评论因" + reason + "已被" + statusLabel(newStatus), comment.getPostId());
            }
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅支持处置帖子或评论");
    }

    private String statusLabel(String status) {
        return HIDDEN.equals(status) ? "下架（内容保留，如认为处理有误会可联系校园服务台）" : "删除";
    }

    private boolean isContentTarget(String targetType) {
        String type = normalize(targetType);
        return TARGET_POST.equals(type) || TARGET_COMMENT.equals(type);
    }

    private long countReports(String status) {
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<CommunityReportEntity>()
                .eq(CommunityReportEntity::getStatus, status));
        return count == null ? 0 : count;
    }

    private ReportSnapshot resolveSnapshot(AuthenticatedUser user, String targetType, Long targetId) {
        switch (targetType) {
            case TARGET_POST -> {
                CommunityPostEntity post = postMapper.selectById(targetId);
                if (post == null || !user.campusId().equals(post.getCampusId())) {
                    throw new BusinessException(ErrorCode.COMMUNITY_REPORT_TARGET_INVALID);
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("postId", post.getId());
                payload.put("title", post.getTitle());
                payload.put("content", post.getContent());
                payload.put("authorId", post.getAuthorId());
                payload.put("status", post.getStatus());
                payload.put("createdAt", String.valueOf(post.getCreatedAt()));
                return new ReportSnapshot(post.getAuthorId(), payload);
            }
            case TARGET_COMMENT -> {
                CommunityCommentEntity comment = commentMapper.selectById(targetId);
                if (comment == null) {
                    throw new BusinessException(ErrorCode.COMMUNITY_REPORT_TARGET_INVALID);
                }
                CommunityPostEntity post = postMapper.selectById(comment.getPostId());
                if (post == null || !user.campusId().equals(post.getCampusId())) {
                    throw new BusinessException(ErrorCode.COMMUNITY_REPORT_TARGET_INVALID);
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("commentId", comment.getId());
                payload.put("postId", comment.getPostId());
                payload.put("content", comment.getContent());
                payload.put("authorId", comment.getAuthorId());
                payload.put("postedAt", String.valueOf(comment.getCreatedAt()));
                return new ReportSnapshot(comment.getAuthorId(), payload);
            }
            case "MESSAGE" -> {
                CommunityDirectMessageEntity message = directMessageMapper.selectById(targetId);
                if (message == null || (!user.userId().equals(message.getSenderId())
                        && !user.userId().equals(message.getReceiverId()))) {
                    throw new BusinessException(ErrorCode.COMMUNITY_REPORT_TARGET_INVALID);
                }
                Long authorId = user.userId().equals(message.getSenderId()) ? message.getReceiverId() : message.getSenderId();
                // 举报私信时，被举报人是"对方"，当前用户是当事人之一，故不能举报自己的判断按对方作者处理
                if (user.userId().equals(message.getSenderId())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不能举报自己发送的私信");
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("messageId", message.getId());
                payload.put("senderId", message.getSenderId());
                payload.put("receiverId", message.getReceiverId());
                payload.put("content", message.getContent());
                payload.put("sentAt", String.valueOf(message.getCreatedAt()));
                return new ReportSnapshot(authorId, payload);
            }
            case "USER" -> {
                UserEntity target = userMapper.selectById(targetId);
                if (target == null) {
                    throw new BusinessException(ErrorCode.COMMUNITY_REPORT_TARGET_INVALID);
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("userId", target.getId());
                payload.put("displayName", target.getDisplayName());
                return new ReportSnapshot(target.getId(), payload);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_PARAMETER, "举报对象类型不合法");
        }
    }

    private AdminCommunityReportResponse toReportResponse(CommunityReportEntity report) {
        TargetBrief brief = resolveTargetBrief(report);
        UserEntity reporter = userMapper.selectById(report.getReporterId());
        return new AdminCommunityReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                brief.title(),
                report.getTargetAuthorId(),
                brief.authorName(),
                report.getReporterId(),
                reporter == null ? null : reporter.getDisplayName(),
                report.getReasonCode(),
                report.getReasonDetail(),
                report.getSnapshot(),
                report.getStatus(),
                report.getHandleNote(),
                report.getHandledBy(),
                report.getHandledAt(),
                report.getCreatedAt());
    }

    private TargetBrief resolveTargetBrief(CommunityReportEntity report) {
        String authorName = null;
        if (report.getTargetAuthorId() != null) {
            UserEntity author = userMapper.selectById(report.getTargetAuthorId());
            authorName = author == null ? null : author.getDisplayName();
        }
        String title = switch (normalize(report.getTargetType())) {
            case TARGET_POST -> {
                CommunityPostEntity post = postMapper.selectById(report.getTargetId());
                yield post == null ? "（帖子已删除）" : post.getTitle();
            }
            case TARGET_COMMENT -> {
                CommunityCommentEntity comment = commentMapper.selectById(report.getTargetId());
                yield comment == null ? "（评论已删除）" : excerpt(comment.getContent(), 40);
            }
            case "MESSAGE" -> {
                CommunityDirectMessageEntity message = directMessageMapper.selectById(report.getTargetId());
                if (message == null) {
                    yield "（私信已删除）";
                }
                yield isNotBlank(message.getContent()) ? excerpt(message.getContent(), 40) : "（图片消息）";
            }
            case "USER" -> "用户举报";
            default -> "（未知对象）";
        };
        return new TargetBrief(title, authorName);
    }

    private void notify(Long recipientId, String title, String content, Long relatedId) {
        if (recipientId == null) {
            return;
        }
        notificationService.send(new NotificationSendCommand(recipientId, NOTIFICATION_TYPE, title,
                excerpt(content, 500), "COMMUNITY_REPORT", relatedId));
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("举报快照序列化失败", exception);
        }
    }

    private String excerpt(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ReportSnapshot(Long authorId, Map<String, Object> payload) {
    }

    private record TargetBrief(String title, String authorName) {
    }
}
