package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.JsonIds;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.*;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.entity.CommunitySectionEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.mapper.CommunitySectionMapper;
import com.studenthub.modules.community.mapper.CommunityTopicMapper;
import com.studenthub.modules.community.mapper.CommunityPostLikeMapper;
import com.studenthub.modules.community.mapper.CommunityCommentLikeMapper;
import com.studenthub.modules.community.entity.CommunityTopicEntity;
import com.studenthub.modules.community.entity.CommunityPostLikeEntity;
import com.studenthub.modules.community.entity.CommunityCommentLikeEntity;
import com.studenthub.modules.community.entity.CommunityUserBookmarkEntity;
import com.studenthub.modules.community.entity.CommunityUserHideEntity;
import com.studenthub.modules.community.mapper.CommunityUserBookmarkMapper;
import com.studenthub.modules.community.mapper.CommunityUserHideMapper;
import com.studenthub.modules.community.service.BannedWordService;
import com.studenthub.modules.community.service.CommunityModerationService;
import com.studenthub.modules.community.service.CommunityService;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.social.service.SocialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {
    private static final String ENABLED = "ENABLED";
    private final CommunitySectionMapper sectionMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityTopicMapper topicMapper;
    private final CommunityPostLikeMapper postLikeMapper;
    private final CommunityCommentLikeMapper commentLikeMapper;
    private final UserMapper userMapper;
    private final FileStorageMapper fileStorageMapper;
    private final FileStorageService fileStorageService;
    private final JwBindingMapper jwBindingMapper;
    private final NotificationService notificationService;
    private final CommunityModerationService communityModerationService;
    private final com.studenthub.modules.achievement.service.AchievementService achievementService;
    private final SocialService socialService;
    private final BannedWordService bannedWordService;
    private final ObjectMapper objectMapper;
    private final CommunityUserBookmarkMapper userBookmarkMapper;
    private final CommunityUserHideMapper userHideMapper;

    @Override
    public List<CommunitySectionResponse> sections(Long campusId) {
        return sectionMapper.selectList(new LambdaQueryWrapper<CommunitySectionEntity>()
                        .eq(CommunitySectionEntity::getCampusId, campusId)
                        .eq(CommunitySectionEntity::getStatus, ENABLED)
                        .orderByAsc(CommunitySectionEntity::getSortOrder))
                .stream().map(CommunitySectionResponse::from).toList();
    }

    @Override
    public List<CommunityTopicResponse> hotTopics(Long campusId) {
        return topicMapper.selectList(new LambdaQueryWrapper<CommunityTopicEntity>()
                        .eq(CommunityTopicEntity::getCampusId, campusId)
                        .eq(CommunityTopicEntity::getStatus, ENABLED)
                        .orderByDesc(CommunityTopicEntity::getHeat).last("LIMIT 8"))
                .stream().map(topic -> new CommunityTopicResponse(topic.getName(), topic.getHeat() == null ? 0 : topic.getHeat())).toList();
    }

    @Override
    public PageResult<CommunityPostResponse> pagePosts(AuthenticatedUser user, Long sectionId, String keyword,
                                                        long pageNum, long pageSize, String sort) {
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 20);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        final String search = normalizedKeyword != null && normalizedKeyword.startsWith("#")
                ? normalizedKeyword.substring(1) : normalizedKeyword;
        boolean hot = "HOT".equalsIgnoreCase(sort);
        // 隐藏过滤（P1 服务端化）：本人「不感兴趣」的帖子不进信息流；
        // 分享/作者主页/搜索直达不受影响（那些走 detail/authorProfile 路径）。
        List<Long> hiddenPostIds = hiddenPostIdsOf(user.userId());
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, user.campusId())
                .eq(CommunityPostEntity::getStatus, ENABLED)
                .eq(sectionId != null, CommunityPostEntity::getSectionId, sectionId)
                .notIn(!hiddenPostIds.isEmpty(), CommunityPostEntity::getId, hiddenPostIds)
                .and(search != null && !search.isBlank(), query -> query
                        .like(CommunityPostEntity::getTitle, search)
                        .or().like(CommunityPostEntity::getContent, search)
                        .or().like(CommunityPostEntity::getTopics, search));
        // 官方置顶（C12）：置顶帖优先展示，再按热度/时间排序
        wrapper.orderByDesc(CommunityPostEntity::getFeedPinned);
        if (hot) {
            wrapper.orderByDesc(CommunityPostEntity::getLikeCount)
                    .orderByDesc(CommunityPostEntity::getCommentCount)
                    .orderByDesc(CommunityPostEntity::getCreatedAt);
        } else {
            wrapper.orderByDesc(CommunityPostEntity::getCreatedAt);
        }
        Page<CommunityPostEntity> page = postMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<CommunityPostResponse> posts = assemblePosts(page.getRecords(), user.userId());
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), posts);
    }

    @Override
    public CommunityPostDetailResponse detail(AuthenticatedUser user, Long postId) {
        CommunityPostEntity post = requirePost(user, postId);
        // 举报人对被举报评论不可见（与举报管理一致），其余人正常可见
        String reportedByViewer = "SELECT target_id FROM community_report WHERE target_type = 'COMMENT' AND reporter_id = "
                + user.userId();
        List<CommunityCommentEntity> comments = commentMapper.selectList(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, postId)
                .eq(CommunityCommentEntity::getStatus, ENABLED)
                .notInSql(CommunityCommentEntity::getId, reportedByViewer)
                .orderByAsc(CommunityCommentEntity::getCreatedAt)
                .last("LIMIT 100"));
        // 批量组装评论（一屏最多 100 条 → 3 条 SQL），作者标记在 Map 重建时补
        Map<Long, CommunityCommentResponse> byId = assembleComments(comments, user.userId());
        List<CommunityCommentResponse> commentResponses = comments.stream()
                .map(comment -> {
                    CommunityCommentResponse base = byId.get(comment.getId());
                    boolean isPostAuthor = post.getAuthorId() != null && post.getAuthorId().equals(comment.getAuthorId());
                    return new CommunityCommentResponse(base.commentId(), base.parentCommentId(), base.authorId(),
                            base.authorName(), base.authorLevel(), base.avatarUrl(), base.authorOfficial(), base.authorOfficialTitle(),
                            base.content(), base.imageUrls(),
                            base.thumbnailUrls(), base.likeCount(), base.weight(), base.liked(), base.pinned(),
                            isPostAuthor, base.createdAt());
                }).toList();
        return new CommunityPostDetailResponse(toPostResponse(post, user.userId()), commentResponses);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityPostResponse publish(AuthenticatedUser user, CreateCommunityPostRequest request) {
        communityModerationService.requireNotMuted(user.userId());
        CommunitySectionEntity section = sectionMapper.selectOne(new LambdaQueryWrapper<CommunitySectionEntity>()
                .eq(CommunitySectionEntity::getId, request.sectionId())
                .eq(CommunitySectionEntity::getCampusId, user.campusId())
                .eq(CommunitySectionEntity::getStatus, ENABLED));
        if (section == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "社区分区不存在或已停用");
        }

        String postType = normalizePostType(request.postType());
        String title;
        String content;
        String blocksJson = null;
        List<Long> imageIds;
        if ("ARTICLE".equals(postType)) {
            if (!StringUtils.hasText(request.title())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章标题不能为空");
            }
            ArticleContent article = buildArticleContent(request.contentBlocks());
            if (article.blocks().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章内容不能为空");
            }
            title = request.title().trim();
            // content 列存纯文本摘要（列表/评论/搜索兼容），完整图文见 content_blocks
            String plain = article.plainText();
            content = plain.isBlank() ? title
                    : (plain.length() > 2000 ? plain.substring(0, 2000) : plain);
            imageIds = article.imageIds();
            blocksJson = writeBlocks(article.blocks());
        } else {
            if (!StringUtils.hasText(request.content())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正文不能为空");
            }
            content = request.content().trim();
            if (content.length() > 2000) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "动态正文不能超过 2000 字");
            }
            title = StringUtils.hasText(request.title()) ? request.title().trim() : deriveTitle(content);
            imageIds = normalizeImageIds(request.imageFileIds());
        }

        List<String> topics = normalizeTopics(request.topics());
        validateOwnedImages(imageIds, user.userId());
        // 免人工审核：命中屏蔽词自动隐藏，通知作者并可申诉
        String status = ENABLED;
        String hiddenReason = null;
        if (bannedWordService.firstHit(user.campusId(), title, content, blocksJson) != null) {
            status = "HIDDEN";
            hiddenReason = "BANNED_WORD";
        }
        CommunityPostEntity entity = new CommunityPostEntity();
        entity.setCampusId(user.campusId());
        entity.setSectionId(section.getId());
        entity.setAuthorId(user.userId());
        entity.setTitle(title);
        entity.setContent(content);
        entity.setPostType(postType);
        entity.setContentBlocks(blocksJson);
        entity.setImageFileIds(JsonIds.write(imageIds));
        entity.setTopics(writeTopics(topics));
        entity.setCommentCount(0);
        entity.setLikeCount(0);
        entity.setReportCount(0);
        entity.setStatus(status);
        entity.setHiddenReason(hiddenReason);
        entity.setLocationName(normalizeLocationText(request.locationName(), 128));
        entity.setLocationAddress(normalizeLocationText(request.locationAddress(), 255));
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setPoiId(normalizeLocationText(request.poiId(), 64));
        if (!ENABLED.equals(status)) {
            entity.setHiddenAt(java.time.LocalDateTime.now());
        }
        postMapper.insert(entity);
        recordTopicUsage(user.campusId(), topics);
        if (!ENABLED.equals(status)) {
            notificationService.send(new NotificationSendCommand(user.userId(), "COMMUNITY_MODERATION",
                    "内容违规提示",
                    "你的帖子「" + title + "」因包含违规内容已被隐藏，可在社区申诉中提交复核。",
                    "COMMUNITY_POST", entity.getId()));
        }
        log.info("社区帖子发布成功, postId={}, authorId={}, section={}, postType={}, status={}",
                entity.getId(), user.userId(), section.getSectionCode(), postType, status);
        return toPostResponse(entity, user.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityCommentResponse comment(AuthenticatedUser user, Long postId, CreateCommunityCommentRequest request) {
        communityModerationService.requireNotMuted(user.userId());
        CommunityPostEntity post = requirePost(user, postId);
        if (request.parentCommentId() != null) {
            CommunityCommentEntity parent = commentMapper.selectById(request.parentCommentId());
            if (parent == null || !post.getId().equals(parent.getPostId()) || !ENABLED.equals(parent.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "回复的评论不存在");
            }
        }
        CommunityCommentEntity entity = new CommunityCommentEntity();
        entity.setPostId(post.getId());
        entity.setAuthorId(user.userId());
        entity.setParentCommentId(request.parentCommentId());
        entity.setContent(request.content().trim());
        List<Long> imageIds = normalizeImageIds(request.imageFileIds());
        validateOwnedImages(imageIds, user.userId());
        entity.setImageFileIds(JsonIds.write(imageIds));
        boolean visible = bannedWordService.firstHit(user.campusId(), request.content()) == null;
        entity.setLikeCount(0);
        entity.setPinned(0);
        entity.setReportCount(0);
        entity.setWeight(0);
        entity.setStatus(visible ? ENABLED : "HIDDEN");
        entity.setHiddenReason(visible ? null : "BANNED_WORD");
        commentMapper.insert(entity);
        if (!visible) {
            notificationService.send(new NotificationSendCommand(user.userId(), "COMMUNITY_MODERATION",
                    "内容违规提示",
                    "你的评论因包含违规内容已被隐藏，可在社区申诉中提交复核。",
                    "COMMUNITY_POST", post.getId()));
        } else {
            if (!post.getAuthorId().equals(user.userId())) {
                notificationService.send(new NotificationSendCommand(post.getAuthorId(), "COMMUNITY_COMMENT", "新的社区评论",
                        user.displayName() + " 评论了你的帖子", "COMMUNITY_POST", post.getId()));
            }
            CommunityPostEntity update = new CommunityPostEntity();
            update.setId(post.getId());
            update.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
            update.setVersion(post.getVersion());
            postMapper.updateById(update);
        }
        return toCommentResponse(entity, user.userId(), post.getAuthorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityPostResponse togglePostLike(AuthenticatedUser user, Long postId) {
        CommunityPostEntity post = requirePost(user, postId);
        CommunityPostLikeEntity existing = postLikeMapper.selectOne(new LambdaQueryWrapper<CommunityPostLikeEntity>()
                .eq(CommunityPostLikeEntity::getPostId, postId).eq(CommunityPostLikeEntity::getUserId, user.userId()));
        if (existing == null) {
            CommunityPostLikeEntity like = new CommunityPostLikeEntity(); like.setPostId(postId); like.setUserId(user.userId()); postLikeMapper.insert(like);
            post.setLikeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1);
            if (!post.getAuthorId().equals(user.userId())) {
                notificationService.send(new NotificationSendCommand(post.getAuthorId(), "COMMUNITY_LIKE", "帖子收到点赞",
                        user.displayName() + " 点赞了你的帖子", "COMMUNITY_POST", post.getId()));
            }
        } else {
            postLikeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, (post.getLikeCount() == null ? 0 : post.getLikeCount()) - 1));
        }
        postMapper.updateById(post);
        return toPostResponse(post, user.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityCommentResponse toggleCommentLike(AuthenticatedUser user, Long commentId) {
        CommunityCommentEntity comment = requireComment(commentId);
        requirePost(user, comment.getPostId());
        CommunityCommentLikeEntity existing = commentLikeMapper.selectOne(new LambdaQueryWrapper<CommunityCommentLikeEntity>()
                .eq(CommunityCommentLikeEntity::getCommentId, commentId).eq(CommunityCommentLikeEntity::getUserId, user.userId()));
        if (existing == null) {
            CommunityCommentLikeEntity like = new CommunityCommentLikeEntity(); like.setCommentId(commentId); like.setUserId(user.userId()); commentLikeMapper.insert(like);
            comment.setLikeCount((comment.getLikeCount() == null ? 0 : comment.getLikeCount()) + 1);
            comment.setWeight((comment.getWeight() == null ? 0 : comment.getWeight()) + 1);
            if (!comment.getAuthorId().equals(user.userId())) {
                notificationService.send(new NotificationSendCommand(comment.getAuthorId(), "COMMUNITY_LIKE", "评论收到点赞",
                        user.displayName() + " 点赞了你的评论", "COMMUNITY_POST", comment.getPostId()));
            }
        } else {
            commentLikeMapper.deleteById(existing.getId());
            comment.setLikeCount(Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) - 1));
            comment.setWeight(Math.max(0, (comment.getWeight() == null ? 0 : comment.getWeight()) - 1));
        }
        commentMapper.updateById(comment);
        CommunityPostEntity post = postMapper.selectById(comment.getPostId());
        return toCommentResponse(comment, user.userId(), post == null ? null : post.getAuthorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityCommentResponse pinComment(AuthenticatedUser user, Long commentId) {
        CommunityCommentEntity comment = requireComment(commentId);
        CommunityPostEntity post = requirePost(user, comment.getPostId());
        if (!post.getAuthorId().equals(user.userId())) throw new BusinessException(ErrorCode.FORBIDDEN, "只有作者可以置顶评论");
        commentMapper.update(null, new LambdaUpdateWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, post.getId()).set(CommunityCommentEntity::getPinned, 0));
        comment.setPinned(comment.getPinned() != null && comment.getPinned() == 1 ? 0 : 1);
        commentMapper.updateById(comment);
        return toCommentResponse(comment, user.userId(), post.getAuthorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(AuthenticatedUser user, Long postId) {
        CommunityPostEntity post = requirePost(user, postId);
        if (!user.userId().equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己发布的帖子");
        }
        // 帖子与全部评论同步逻辑删除；发帖/评论经验按"经验可回收"由 AchievementService 业务重算自动扣减
        post.setStatus("DELETED");
        postMapper.updateById(post);
        commentMapper.update(null, new LambdaUpdateWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, post.getId())
                .ne(CommunityCommentEntity::getStatus, "DELETED")
                .set(CommunityCommentEntity::getStatus, "DELETED"));
        log.info("社区帖子已由作者删除, postId={}, authorId={}", postId, user.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(AuthenticatedUser user, Long commentId) {
        CommunityCommentEntity comment = requireComment(commentId);
        if (!user.userId().equals(comment.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        comment.setStatus("DELETED");
        commentMapper.updateById(comment);
        CommunityPostEntity post = requirePost(user, comment.getPostId());
        post.setCommentCount(Math.max(0, (post.getCommentCount() == null ? 0 : post.getCommentCount()) - 1));
        postMapper.updateById(post);
    }

    @Override
    public CommunityAuthorProfileResponse authorProfile(AuthenticatedUser user, Long authorId) {
        UserEntity author = userMapper.selectById(authorId);
        if (author == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        List<CommunityPostResponse> posts = postMapper.selectList(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getAuthorId, authorId).eq(CommunityPostEntity::getCampusId, user.campusId())
                .eq(CommunityPostEntity::getStatus, ENABLED).orderByDesc(CommunityPostEntity::getCreatedAt).last("LIMIT 20"))
                .stream().map(post -> toPostResponse(post, user.userId())).toList();
        JwBindingEntity binding = jwBindingMapper.findByUserId(authorId);
        String college = firstNonBlank(binding == null ? null : binding.getCollege(), author.getCollege(), "学院未填写");
        String major = firstNonBlank(binding == null ? null : binding.getMajor(), "专业未填写");
        String className = firstNonBlank(binding == null ? null : binding.getClassName(), "班级未填写");
        // 隐私脱敏（API需求清单 二-5）：作者关闭"公开年级与专业"时学业字段置空；关闭"公开我的勋章"时不展示佩戴勋章。
        // 本人查看自己主页始终全量。
        boolean selfView = user.userId().equals(authorId);
        if (!selfView && !socialService.showAcademicInfo(authorId)) {
            college = null;
            major = null;
            className = null;
        }
        String wornBadge = selfView || socialService.showMedals(authorId) ? author.getWornBadgeCode() : null;
        String gender = firstNonBlank(author.getGender(), "性别未填写");
        // 等级/经验与作者本人成长档案同口径实时重算（AchievementService 单点）
        int totalExp = achievementService.totalExpOf(authorId);
        int level = com.studenthub.modules.achievement.service.AchievementService.levelOf(totalExp);
        boolean isFollowing = followMapperCountSafe(user.userId(), authorId);
        return new CommunityAuthorProfileResponse(author.getId(), author.getDisplayName(), avatarUrl(author),
                Boolean.TRUE.equals(author.getOfficial()), author.getOfficialTitle(),
                level, com.studenthub.modules.achievement.service.AchievementService.levelName(level), totalExp,
                wornBadge, isFollowing,
                college, major, className, gender, posts);
    }

    /** 关注态查询（user_follow 落地后接入作者主页 isFollowing，需求清单 二-5） */
    private boolean followMapperCountSafe(Long viewerId, Long authorId) {
        return socialService.isFollowing(viewerId, authorId);
    }

    // ==================== 用户行为服务端化（P1）：收藏与隐藏 ====================

    @Override
    public boolean toggleBookmark(AuthenticatedUser user, Long postId) {
        requirePost(user, postId);
        CommunityUserBookmarkEntity existing = userBookmarkMapper.selectOne(
                new LambdaQueryWrapper<CommunityUserBookmarkEntity>()
                        .eq(CommunityUserBookmarkEntity::getUserId, user.userId())
                        .eq(CommunityUserBookmarkEntity::getPostId, postId));
        if (existing == null) {
            CommunityUserBookmarkEntity bookmark = new CommunityUserBookmarkEntity();
            bookmark.setCampusId(user.campusId());
            bookmark.setUserId(user.userId());
            bookmark.setPostId(postId);
            userBookmarkMapper.insert(bookmark);
            return true;
        }
        userBookmarkMapper.deleteById(existing.getId());
        return false;
    }

    @Override
    public boolean toggleHide(AuthenticatedUser user, Long postId) {
        requirePost(user, postId);
        CommunityUserHideEntity existing = userHideMapper.selectOne(
                new LambdaQueryWrapper<CommunityUserHideEntity>()
                        .eq(CommunityUserHideEntity::getUserId, user.userId())
                        .eq(CommunityUserHideEntity::getPostId, postId));
        if (existing == null) {
            CommunityUserHideEntity hide = new CommunityUserHideEntity();
            hide.setCampusId(user.campusId());
            hide.setUserId(user.userId());
            hide.setPostId(postId);
            userHideMapper.insert(hide);
            return true;
        }
        userHideMapper.deleteById(existing.getId());
        return false;
    }

    @Override
    public List<Long> listBehaviorPostIds(AuthenticatedUser user, String kind) {
        boolean bookmarks = "BOOKMARK".equalsIgnoreCase(kind);
        if (!bookmarks && !"HIDE".equalsIgnoreCase(kind)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "kind 仅支持 BOOKMARK / HIDE");
        }
        return bookmarks
                ? userBookmarkMapper.selectList(new LambdaQueryWrapper<CommunityUserBookmarkEntity>()
                        .eq(CommunityUserBookmarkEntity::getUserId, user.userId()))
                        .stream().map(CommunityUserBookmarkEntity::getPostId).toList()
                : userHideMapper.selectList(new LambdaQueryWrapper<CommunityUserHideEntity>()
                        .eq(CommunityUserHideEntity::getUserId, user.userId()))
                        .stream().map(CommunityUserHideEntity::getPostId).toList();
    }

    @Override
    public PageResult<CommunityBookmarkItemResponse> pageBookmarks(AuthenticatedUser user, long pageNum, long pageSize) {
        return pageBehaviorItems(user, pageNum, pageSize, true);
    }

    @Override
    public PageResult<CommunityBookmarkItemResponse> pageHidden(AuthenticatedUser user, long pageNum, long pageSize) {
        return pageBehaviorItems(user, pageNum, pageSize, false);
    }

    /** 收藏/隐藏列表共用分页：按操作时间倒序；帖子已被删/下架时以 removed=true 返回（客户端展示失效态+清理入口） */
    private PageResult<CommunityBookmarkItemResponse> pageBehaviorItems(
            AuthenticatedUser user, long pageNum, long pageSize, boolean bookmarks) {
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 50);
        Page<CommunityUserBookmarkEntity> bookmarkPage = null;
        Page<CommunityUserHideEntity> hidePage = null;
        if (bookmarks) {
            bookmarkPage = userBookmarkMapper.selectPage(new Page<>(safePage, safeSize),
                    new LambdaQueryWrapper<CommunityUserBookmarkEntity>()
                            .eq(CommunityUserBookmarkEntity::getUserId, user.userId())
                            .orderByDesc(CommunityUserBookmarkEntity::getCreatedAt));
        } else {
            hidePage = userHideMapper.selectPage(new Page<>(safePage, safeSize),
                    new LambdaQueryWrapper<CommunityUserHideEntity>()
                            .eq(CommunityUserHideEntity::getUserId, user.userId())
                            .orderByDesc(CommunityUserHideEntity::getCreatedAt));
        }
        List<Long> postIds = bookmarks
                ? bookmarkPage.getRecords().stream().map(CommunityUserBookmarkEntity::getPostId).toList()
                : hidePage.getRecords().stream().map(CommunityUserHideEntity::getPostId).toList();
        List<java.time.LocalDateTime> actedAt = bookmarks
                ? bookmarkPage.getRecords().stream().map(CommunityUserBookmarkEntity::getCreatedAt).toList()
                : hidePage.getRecords().stream().map(CommunityUserHideEntity::getCreatedAt).toList();
        Map<Long, CommunityPostEntity> posts = postIds.isEmpty() ? Map.of()
                : postMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(CommunityPostEntity::getId,
                                java.util.function.Function.identity(), (a, b) -> a));
        List<CommunityBookmarkItemResponse> items = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i++) {
            CommunityPostEntity post = posts.get(postIds.get(i));
            // removed=true：帖子已删（作者删或治理下架），列表不可点入，客户端提供取消收藏/隐藏清理操作
            boolean removed = post == null || !"ENABLED".equals(post.getStatus());
            items.add(new CommunityBookmarkItemResponse(
                    postIds.get(i),
                    post == null ? "" : post.getTitle(),
                    removed ? "REMOVED" : "ACTIVE",
                    removed,
                    post == null ? null : post.getStatus(),
                    actedAt.get(i)));
        }
        long total = bookmarks ? bookmarkPage.getTotal() : hidePage.getTotal();
        return PageResult.of(safePage, safeSize, total, items);
    }

    /** 当前用户已隐藏的帖子 id（pagePosts 过滤用）；隐藏量级小（百级内），全量取出无压力 */
    private List<Long> hiddenPostIdsOf(Long userId) {
        return userHideMapper.selectList(new LambdaQueryWrapper<CommunityUserHideEntity>()
                        .eq(CommunityUserHideEntity::getUserId, userId))
                .stream().map(CommunityUserHideEntity::getPostId).toList();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String value, String secondary, String fallback) {
        return value == null || value.isBlank()
                ? (secondary == null || secondary.isBlank() ? fallback : secondary) : value;
    }

    private CommunityPostEntity requirePost(AuthenticatedUser user, Long postId) {
        CommunityPostEntity post = postMapper.selectById(postId);
        if (post == null || !user.campusId().equals(post.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在或已下线");
        }
        // 作者可查看自己被隐藏（屏蔽词/治理下架）的帖子，用于申诉与状态提示；其他人只能看 ENABLED
        boolean ownerView = user.userId().equals(post.getAuthorId()) && "HIDDEN".equals(post.getStatus());
        if (!ENABLED.equals(post.getStatus()) && !ownerView) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在或已下线");
        }
        return post;
    }

    /** 单条路径 = 批量组装退化为 1 条（详情/发布/点赞等复用），查询形态与批量一致 */
    private CommunityPostResponse toPostResponse(CommunityPostEntity post, Long viewerId) {
        return assemblePosts(List.of(post), viewerId).get(0);
    }

    /** 单条评论组装：postAuthorId 语义保留（作者标记） */
    private CommunityCommentResponse toCommentResponse(CommunityCommentEntity comment, Long viewerId, Long postAuthorId) {
        CommunityCommentResponse response = assembleComments(List.of(comment), viewerId).get(comment.getId());
        boolean isPostAuthor = postAuthorId != null && postAuthorId.equals(comment.getAuthorId());
        return new CommunityCommentResponse(response.commentId(), response.parentCommentId(), response.authorId(),
                response.authorName(), response.authorLevel(), response.avatarUrl(), response.authorOfficial(), response.authorOfficialTitle(),
                response.content(), response.imageUrls(),
                response.thumbnailUrls(), response.likeCount(), response.weight(), response.liked(), response.pinned(),
                isPostAuthor, response.createdAt());
    }

    /**
     * 批量组装（2026-10-17 定稿：31 SQL/屏 → 4 条）：
     * ①selectBatchIds 作者行（avatarUrl/wornBadgeCode 整行搭车）②selectBatchIds 分区行
     * ③IN 查询当前用户对全部帖子的点赞态 ④内存 Map 组装。
     * authorLevel 列表场景不展示（等级=f(exp) 需逐人业务数据，个人中心 /achievements/profile 下发）；
     * authorBadgeCode 为佩戴列搭车零成本。
     */
    private List<CommunityPostResponse> assemblePosts(List<CommunityPostEntity> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = posts.stream().map(CommunityPostEntity::getAuthorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> sectionIds = posts.stream().map(CommunityPostEntity::getSectionId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> postIds = posts.stream().map(CommunityPostEntity::getId).collect(Collectors.toSet());

        Map<Long, UserEntity> authors = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, java.util.function.Function.identity(), (a, b) -> a));
        Map<Long, CommunitySectionEntity> sections = sectionIds.isEmpty() ? Map.of()
                : sectionMapper.selectBatchIds(sectionIds).stream()
                        .collect(Collectors.toMap(CommunitySectionEntity::getId, java.util.function.Function.identity(), (a, b) -> a));
        Set<Long> likedPostIds = viewerId == null || postIds.isEmpty() ? Set.of()
                : postLikeMapper.selectList(new LambdaQueryWrapper<CommunityPostLikeEntity>()
                        .eq(CommunityPostLikeEntity::getUserId, viewerId)
                        .in(CommunityPostLikeEntity::getPostId, postIds))
                        .stream().map(CommunityPostLikeEntity::getPostId).collect(Collectors.toSet());
        Set<Long> bookmarkedPostIds = viewerId == null || postIds.isEmpty() ? Set.of()
                : userBookmarkMapper.selectList(new LambdaQueryWrapper<CommunityUserBookmarkEntity>()
                        .eq(CommunityUserBookmarkEntity::getUserId, viewerId)
                        .in(CommunityUserBookmarkEntity::getPostId, postIds))
                        .stream().map(CommunityUserBookmarkEntity::getPostId).collect(Collectors.toSet());
        // 作者真实等级批量算（等级=f(总经验)，与作者主页/成就页同口径；2026-11-05 等级统一）
        Map<Long, Integer> authorExp = achievementService.totalExpBatch(authorIds);

        return posts.stream().map(post -> {
            UserEntity author = authors.get(post.getAuthorId());
            CommunitySectionEntity section = sections.get(post.getSectionId());
            Integer authorExpValue = authorExp.get(post.getAuthorId());
            Integer authorLevelValue = authorExpValue == null ? null : com.studenthub.modules.achievement.service.AchievementService.levelOf(authorExpValue);
            return new CommunityPostResponse(post.getId(), post.getSectionId(),
                    section == null ? null : section.getSectionName(), section == null ? null : section.getIcon(),
                    post.getAuthorId(), author == null ? "同学" : author.getDisplayName(), avatarUrl(author),
                    authorLevelValue,
                    author == null ? null : author.getWornBadgeCode(),
                    author != null && Boolean.TRUE.equals(author.getOfficial()),
                    author == null ? null : author.getOfficialTitle(),
                    post.getPostType() == null ? "DYNAMIC" : post.getPostType(),
                    post.getStatus(), post.getHiddenReason(),
                    post.getTitle(), post.getContent(),
                    signImages(JsonIds.parse(post.getImageFileIds())),
                    signThumbnailImages(JsonIds.parse(post.getImageFileIds())),
                    parseBlockResponses(post.getContentBlocks(), post.getId()),
                    parseTopics(post.getTopics()),
                    post.getCommentCount() == null ? 0 : post.getCommentCount(),
                    post.getLikeCount() == null ? 0 : post.getLikeCount(),
                    likedPostIds.contains(post.getId()), bookmarkedPostIds.contains(post.getId()),
                    post.getCreatedAt(),
                    post.getLocationName(), post.getLocationAddress(),
                    post.getLatitude(), post.getLongitude(), post.getPoiId());
        }).toList();
    }

    /** 批量组装评论：同样 4 条；返回按评论 ID 索引，供调用点按序取用 */
    private Map<Long, CommunityCommentResponse> assembleComments(List<CommunityCommentEntity> comments, Long viewerId) {
        if (comments.isEmpty()) {
            return Map.of();
        }
        Set<Long> authorIds = comments.stream().map(CommunityCommentEntity::getAuthorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> commentIds = comments.stream().map(CommunityCommentEntity::getId).collect(Collectors.toSet());
        Map<Long, UserEntity> authors = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, java.util.function.Function.identity(), (a, b) -> a));
        Set<Long> likedCommentIds = viewerId == null || commentIds.isEmpty() ? Set.of()
                : commentLikeMapper.selectList(new LambdaQueryWrapper<CommunityCommentLikeEntity>()
                        .eq(CommunityCommentLikeEntity::getUserId, viewerId)
                        .in(CommunityCommentLikeEntity::getCommentId, commentIds))
                        .stream().map(CommunityCommentLikeEntity::getCommentId).collect(Collectors.toSet());
        // 作者真实等级批量算（等级=f(总经验)，与作者主页/成就页同口径）
        Map<Long, Integer> authorExp = achievementService.totalExpBatch(authorIds);
        Map<Long, CommunityCommentResponse> result = new java.util.LinkedHashMap<>();
        for (CommunityCommentEntity comment : comments) {
            UserEntity author = authors.get(comment.getAuthorId());
            Integer authorExpValue = authorExp.get(comment.getAuthorId());
            Integer authorLevelValue = authorExpValue == null ? null : com.studenthub.modules.achievement.service.AchievementService.levelOf(authorExpValue);
            result.put(comment.getId(), new CommunityCommentResponse(comment.getId(), comment.getParentCommentId(),
                    comment.getAuthorId(), author == null ? "同学" : author.getDisplayName(), authorLevelValue, avatarUrl(author),
                    author != null && Boolean.TRUE.equals(author.getOfficial()),
                    author == null ? null : author.getOfficialTitle(),
                    comment.getContent(), signImages(JsonIds.parse(comment.getImageFileIds())),
                    signThumbnailImages(JsonIds.parse(comment.getImageFileIds())),
                    comment.getLikeCount() == null ? 0 : comment.getLikeCount(),
                    comment.getWeight() == null ? 0 : comment.getWeight(),
                    likedCommentIds.contains(comment.getId()),
                    comment.getPinned() != null && comment.getPinned() == 1,
                    false, comment.getCreatedAt()));
        }
        return result;
    }

    private CommunityCommentEntity requireComment(Long id) {
        CommunityCommentEntity comment = commentMapper.selectById(id);
        if (comment == null || !ENABLED.equals(comment.getStatus())) throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在或已删除");
        return comment;
    }

    private String avatarUrl(UserEntity user) {
        return user == null ? null : fileStorageService.resolveAvatarUrl(user.getAvatarUrl());
    }

    /** 帖子类型归一：空 → DYNAMIC；非法值拒绝 */
    private String normalizePostType(String postType) {
        if (!StringUtils.hasText(postType)) {
            return "DYNAMIC";
        }
        String value = postType.trim().toUpperCase();
        if (!"DYNAMIC".equals(value) && !"ARTICLE".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "帖子类型仅支持 DYNAMIC / ARTICLE");
        }
        return value;
    }

    /** 动态未填标题时取正文前 40 字（title 列 NOT NULL） */
    private String deriveTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40) + "…";
    }

    /**
     * 文章图文混排块校验与归一：TEXT 非空累计（≤10000 字）；IMAGE 需 fileId（≤9 张）；
     * 返回归一化块列表 + 纯文本摘要 + 去重后的图片 ID（顺序保持首次出现）。
     */
    private ArticleContent buildArticleContent(List<CommunityContentBlockRequest> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArticleContent(List.of(), "", List.of());
        }
        List<ArticleBlock> normalized = new ArrayList<>();
        List<Long> imageIds = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int textLength = 0;
        for (CommunityContentBlockRequest block : blocks) {
            if (block == null) {
                continue;
            }
            String type = block.type() == null ? "" : block.type().trim().toUpperCase();
            if ("TEXT".equals(type)) {
                String value = block.text() == null ? "" : block.text().trim();
                if (value.isEmpty()) {
                    continue;
                }
                textLength += value.length();
                if (textLength > 10000) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章正文合计不能超过 10000 字");
                }
                normalized.add(new ArticleBlock("TEXT", value, null));
                if (!plain.isEmpty()) {
                    plain.append("\n\n");
                }
                plain.append(value);
            } else if ("IMAGE".equals(type)) {
                if (block.fileId() == null || block.fileId() <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片块缺少文件 ID");
                }
                if (imageIds.size() >= 9) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章图片最多 9 张");
                }
                imageIds.add(block.fileId());
                normalized.add(new ArticleBlock("IMAGE", null, block.fileId()));
            } else {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "内容块类型仅支持 TEXT / IMAGE");
            }
        }
        return new ArticleContent(normalized, plain.toString(), normalizeImageIds(imageIds));
    }

    private String writeBlocks(List<ArticleBlock> blocks) {
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文章内容序列化失败");
        }
    }

    /** 响应组装：IMAGE 块批量签名（一次查询），TEXT 块原样返回 */
    private List<CommunityContentBlockResponse> parseBlockResponses(String json, Long postId) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        List<ArticleBlock> blocks;
        try {
            blocks = objectMapper.readValue(json, new TypeReference<List<ArticleBlock>>() {
            });
        } catch (Exception exception) {
            log.warn("帖子内容块解析失败, postId={}", postId);
            return List.of();
        }
        List<Long> imageIds = blocks.stream()
                .filter(block -> "IMAGE".equalsIgnoreCase(block.type()) && block.fileId() != null)
                .map(ArticleBlock::fileId)
                .distinct()
                .toList();
        List<String> urls = signImages(imageIds);
        List<String> thumbnails = signThumbnailImages(imageIds);
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < imageIds.size(); i++) {
            index.put(imageIds.get(i), i);
        }
        List<CommunityContentBlockResponse> result = new ArrayList<>();
        for (ArticleBlock block : blocks) {
            if ("IMAGE".equalsIgnoreCase(block.type()) && block.fileId() != null) {
                Integer position = index.get(block.fileId());
                result.add(new CommunityContentBlockResponse("IMAGE", null, block.fileId(),
                        position == null ? null : urls.get(position),
                        position == null ? null : thumbnails.get(position)));
            } else if ("TEXT".equalsIgnoreCase(block.type())) {
                result.add(new CommunityContentBlockResponse("TEXT", block.text(), null, null, null));
            }
        }
        return result;
    }

    private record ArticleBlock(String type, String text, Long fileId) {
    }

    private record ArticleContent(List<ArticleBlock> blocks, String plainText, List<Long> imageIds) {
    }

    private List<String> signImages(List<Long> ids) {
        return ids.isEmpty() ? List.of() : fileStorageService.signViewPaths(ids);
    }

    private List<String> signThumbnailImages(List<Long> ids) {
        return ids.isEmpty() ? List.of() : ids.stream().map(fileStorageService::signThumbnailPath).toList();
    }

    private void validateOwnedImages(List<Long> ids, Long userId) {
        for (Long id : ids) {
            FileStorageEntity file = fileStorageMapper.selectById(id);
            if (file == null || !userId.equals(file.getUploaderId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能使用自己上传的图片");
            }
        }
    }

    private List<Long> normalizeImageIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> result = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || result.contains(id)) continue;
            result.add(id);
            if (result.size() == 9) break;
        }
        return result;
    }

    private List<String> normalizeTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) return List.of();
        return topics.stream()
                .filter(topic -> topic != null && !topic.isBlank())
                .map(String::trim)
                .map(topic -> topic.startsWith("#") ? topic.substring(1).trim() : topic)
                .filter(topic -> !topic.isBlank())
                .distinct()
                .limit(8)
                .toList();
    }

    private String writeTopics(List<String> topics) {
        return topics.isEmpty() ? null : String.join(",", topics);
    }

    private void recordTopicUsage(Long campusId, List<String> topics) {
        for (String name : topics) {
            CommunityTopicEntity existing = topicMapper.selectOne(new LambdaQueryWrapper<CommunityTopicEntity>()
                    .eq(CommunityTopicEntity::getCampusId, campusId)
                    .eq(CommunityTopicEntity::getName, name));
            if (existing != null) {
                topicMapper.incrementHeat(existing.getId());
                continue;
            }
            CommunityTopicEntity topic = new CommunityTopicEntity();
            topic.setCampusId(campusId);
            topic.setName(name);
            topic.setHeat(1);
            topic.setStatus(ENABLED);
            topicMapper.insert(topic);
        }
    }

    private List<String> parseTopics(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).filter(s -> !s.isBlank()).map(String::trim).toList();
    }


    private String normalizeLocationText(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
