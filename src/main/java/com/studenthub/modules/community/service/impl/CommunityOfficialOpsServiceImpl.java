package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.JsonIds;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminOfficialPinRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialPublishRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialPublishResponse;
import com.studenthub.modules.community.controller.dto.AdminOfficialReplyRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialReplyResponse;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.entity.CommunitySectionEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.mapper.CommunitySectionMapper;
import com.studenthub.modules.community.service.BannedWordService;
import com.studenthub.modules.community.service.CommunityOfficialOpsService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 官方号运营实现（C12）：置顶走 UpdateWrapper 显式置空 pinned_at/pinned_by；
 * 官方回复写入 community_comment 并维护帖子评论数；代发帖子走 community_post，
 * 命中屏蔽词时直接拒绝而不是悄悄隐藏官方内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityOfficialOpsServiceImpl implements CommunityOfficialOpsService {

    private static final String ENABLED = "ENABLED";
    private static final String HIDDEN = "HIDDEN";
    private static final String NOTIFICATION_TYPE = "COMMUNITY_MODERATION";
    /** 允许以官方身份发声的账号类型：官网抓取号 / 人工运营号 */
    private static final Set<String> OFFICIAL_POSTING_TYPES = Set.of("OFFICIAL_AUTO", "OFFICIAL_HUMAN");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunitySectionMapper sectionMapper;
    private final UserMapper userMapper;
    private final BannedWordService bannedWordService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinPost(Long postId, AdminOfficialPinRequest request) {
        CommunityPostEntity post = requirePost(postId);
        boolean pinned = Boolean.TRUE.equals(request.pinned());
        Long operatorId = currentUserProvider.requireCurrentUser().userId();
        // updateById 会忽略 null 字段，取消置顶必须用 UpdateWrapper 显式清空 pinned_at/pinned_by
        LambdaUpdateWrapper<CommunityPostEntity> update = new LambdaUpdateWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getId, post.getId())
                .set(CommunityPostEntity::getFeedPinned, pinned ? 1 : 0)
                .set(CommunityPostEntity::getPinnedAt, pinned ? LocalDateTime.now(ZONE) : null)
                .set(CommunityPostEntity::getPinnedBy, pinned ? operatorId : null)
                .setSql("version = version + 1");
        postMapper.update(null, update);
        log.info("社区帖子置顶状态更新, postId={}, pinned={}, operatorId={}",
                post.getId(), pinned, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialReplyResponse replyAsOfficial(Long postId, AdminOfficialReplyRequest request) {
        postMapper.selectByIdForUpdate(postId);
        CommunityPostEntity post = requirePost(postId);
        if (!ENABLED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "帖子已下架，不能官方回复");
        }
        UserEntity official = requireOfficialAccount(request.officialUserId());
        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "回复内容不能为空");
        }
        String hit = bannedWordService.firstHit(official.getCampusId(), content);
        if (hit != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "回复内容命中屏蔽词「" + hit + "」，请修改后再发送");
        }
        CommunityCommentEntity comment = new CommunityCommentEntity();
        comment.setPostId(post.getId());
        comment.setAuthorId(official.getId());
        comment.setParentCommentId(null);
        comment.setContent(content);
        comment.setImageFileIds(null);
        comment.setLikeCount(0);
        comment.setPinned(0);
        comment.setStatus(ENABLED);
        comment.setHiddenReason(null);
        comment.setReportCount(0);
        comment.setWeight(0);
        commentMapper.insert(comment);
        // 维护帖子评论数（与小程序发评论同口径）
        postMapper.adjustCommentCount(post.getId(), 1);
        if (!official.getId().equals(post.getAuthorId())) {
            notificationService.send(new NotificationSendCommand(post.getAuthorId(), NOTIFICATION_TYPE,
                    "官方回复了你的帖子",
                    (StringUtils.hasText(official.getDisplayName()) ? official.getDisplayName() : "官方账号")
                            + " 回复了你的帖子「" + post.getTitle() + "」：" + excerpt(content, 120),
                    "COMMUNITY_POST", post.getId()));
        }
        log.info("官方账号回复帖子, commentId={}, postId={}, officialUserId={}, operatorId={}",
                comment.getId(), post.getId(), official.getId(),
                currentUserProvider.requireCurrentUser().userId());
        return new AdminOfficialReplyResponse(comment.getId(), post.getId(), official.getId(),
                official.getDisplayName(), content, comment.getCreatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOfficialPublishResponse publishAsOfficial(AdminOfficialPublishRequest request) {
        UserEntity official = requireOfficialAccount(request.officialUserId());
        CommunitySectionEntity section = sectionMapper.selectById(request.sectionId());
        if (section == null || !official.getCampusId().equals(section.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标分区不存在或不属于当前校区");
        }
        if (!ENABLED.equals(section.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "目标分区已停用，不能发布");
        }
        String title = request.title().trim();
        String content = request.content().trim();
        List<Long> imageIds = request.imageFileIds() == null ? List.of()
                : request.imageFileIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (imageIds.size() > 9) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片最多 9 张");
        }
        String hit = bannedWordService.firstHit(official.getCampusId(), title, content);
        if (hit != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "内容命中屏蔽词「" + hit + "」，请修改后再发布（官方内容不做静默隐藏）");
        }
        String topics = normalizeTopics(request.topics());
        CommunityPostEntity post = new CommunityPostEntity();
        post.setCampusId(official.getCampusId());
        post.setSectionId(section.getId());
        post.setAuthorId(official.getId());
        post.setTitle(title);
        post.setContent(content);
        post.setPostType(imageIds.isEmpty() ? "DYNAMIC" : "ARTICLE");
        post.setContentBlocks(null);
        post.setImageFileIds(JsonIds.write(imageIds));
        post.setTopics(topics);
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setReportCount(0);
        post.setStatus(ENABLED);
        post.setHiddenReason(null);
        postMapper.insert(post);
        log.info("官方账号代发帖子, postId={}, officialUserId={}, sectionId={}, operatorId={}",
                post.getId(), official.getId(), section.getId(),
                currentUserProvider.requireCurrentUser().userId());
        return new AdminOfficialPublishResponse(post.getId(), official.getId(),
                official.getDisplayName(), post.getPostType(), post.getStatus(), post.getCreatedAt());
    }

    private CommunityPostEntity requirePost(Long postId) {
        CommunityPostEntity post = postMapper.selectByIdIncludingDeleted(postId);
        if (post == null || !campusContextService.currentCampusId().equals(post.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在或不属于当前校区");
        }
        if (post.getDeleted() != null && post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "帖子已删除，不能执行该操作");
        }
        return post;
    }

    /** 官方账号校验：类型/启用态/校区三重约束，避免把广告服务号或普通用户当官方身份使用 */
    private UserEntity requireOfficialAccount(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "官方账号不存在");
        }
        String accountType = user.getAccountType() == null ? "NORMAL" : user.getAccountType();
        if (!OFFICIAL_POSTING_TYPES.contains(accountType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "仅 OFFICIAL_AUTO / OFFICIAL_HUMAN 账号可作为官方身份发声（当前：" + accountType + "）");
        }
        if (!ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该官方账号已停用，不能发声");
        }
        if (user.getCampusId() != null && !user.getCampusId().equals(campusContextService.currentCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "官方账号不属于当前校区");
        }
        return user;
    }

    private String normalizeTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return null;
        }
        List<String> cleaned = topics.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    private String excerpt(String text, int max) {
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
