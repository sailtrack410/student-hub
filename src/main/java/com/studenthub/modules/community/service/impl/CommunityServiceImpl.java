package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, user.campusId())
                .eq(CommunityPostEntity::getStatus, ENABLED)
                .eq(sectionId != null, CommunityPostEntity::getSectionId, sectionId)
                .and(search != null && !search.isBlank(), query -> query
                        .like(CommunityPostEntity::getTitle, search)
                        .or().like(CommunityPostEntity::getContent, search)
                        .or().like(CommunityPostEntity::getTopics, search));
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
        List<CommunityCommentEntity> comments = commentMapper.selectList(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, postId)
                .eq(CommunityCommentEntity::getStatus, ENABLED)
                .orderByAsc(CommunityCommentEntity::getCreatedAt)
                .last("LIMIT 100"));
        // 批量组装评论（一屏最多 100 条 → 3 条 SQL），作者标记在 Map 重建时补
        Map<Long, CommunityCommentResponse> byId = assembleComments(comments, user.userId());
        List<CommunityCommentResponse> commentResponses = comments.stream()
                .map(comment -> {
                    CommunityCommentResponse base = byId.get(comment.getId());
                    boolean isPostAuthor = post.getAuthorId() != null && post.getAuthorId().equals(comment.getAuthorId());
                    return new CommunityCommentResponse(base.commentId(), base.parentCommentId(), base.authorId(),
                            base.authorName(), base.avatarUrl(), base.content(), base.imageUrls(),
                            base.thumbnailUrls(), base.likeCount(), base.liked(), base.pinned(),
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
        List<Long> imageIds = normalizeImageIds(request.imageFileIds());
        List<String> topics = normalizeTopics(request.topics());
        validateOwnedImages(imageIds, user.userId());
        CommunityPostEntity entity = new CommunityPostEntity();
        entity.setCampusId(user.campusId());
        entity.setSectionId(section.getId());
        entity.setAuthorId(user.userId());
        entity.setTitle(request.title().trim());
        entity.setContent(request.content().trim());
        entity.setImageFileIds(JsonIds.write(imageIds));
        entity.setTopics(writeTopics(topics));
        entity.setCommentCount(0);
        entity.setLikeCount(0);
        entity.setStatus(ENABLED);
        postMapper.insert(entity);
        recordTopicUsage(user.campusId(), topics);
        log.info("社区帖子发布成功, postId={}, authorId={}, section={}", entity.getId(), user.userId(), section.getSectionCode());
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
        entity.setLikeCount(0);
        entity.setPinned(0);
        entity.setStatus(ENABLED);
        commentMapper.insert(entity);
        if (!post.getAuthorId().equals(user.userId())) {
            notificationService.send(new NotificationSendCommand(post.getAuthorId(), "COMMUNITY_COMMENT", "新的社区评论",
                    user.displayName() + " 评论了你的帖子", "COMMUNITY_POST", post.getId()));
        }
        CommunityPostEntity update = new CommunityPostEntity();
        update.setId(post.getId());
        update.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
        update.setVersion(post.getVersion());
        postMapper.updateById(update);
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
            if (!comment.getAuthorId().equals(user.userId())) {
                notificationService.send(new NotificationSendCommand(comment.getAuthorId(), "COMMUNITY_LIKE", "评论收到点赞",
                        user.displayName() + " 点赞了你的评论", "COMMUNITY_POST", comment.getPostId()));
            }
        } else {
            commentLikeMapper.deleteById(existing.getId());
            comment.setLikeCount(Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) - 1));
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
        String gender = firstNonBlank(author.getGender(), "性别未填写");
        // 等级/经验与作者本人成长档案同口径实时重算（AchievementService 单点）
        int totalExp = achievementService.totalExpOf(authorId);
        int level = com.studenthub.modules.achievement.service.AchievementService.levelOf(totalExp);
        return new CommunityAuthorProfileResponse(author.getId(), author.getDisplayName(), avatarUrl(author),
                level, com.studenthub.modules.achievement.service.AchievementService.levelName(level), totalExp,
                author.getWornBadgeCode(), false,
                college, major, className, gender, posts);
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
        if (post == null || !user.campusId().equals(post.getCampusId()) || !ENABLED.equals(post.getStatus())) {
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
                response.authorName(), response.avatarUrl(), response.content(), response.imageUrls(),
                response.thumbnailUrls(), response.likeCount(), response.liked(), response.pinned(),
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

        return posts.stream().map(post -> {
            UserEntity author = authors.get(post.getAuthorId());
            CommunitySectionEntity section = sections.get(post.getSectionId());
            return new CommunityPostResponse(post.getId(), post.getSectionId(),
                    section == null ? null : section.getSectionName(), section == null ? null : section.getIcon(),
                    post.getAuthorId(), author == null ? "同学" : author.getDisplayName(), avatarUrl(author),
                    null,
                    author == null ? null : author.getWornBadgeCode(),
                    post.getTitle(), post.getContent(),
                    signImages(JsonIds.parse(post.getImageFileIds())),
                    signThumbnailImages(JsonIds.parse(post.getImageFileIds())), parseTopics(post.getTopics()),
                    post.getCommentCount() == null ? 0 : post.getCommentCount(),
                    post.getLikeCount() == null ? 0 : post.getLikeCount(),
                    likedPostIds.contains(post.getId()), post.getCreatedAt());
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
        Map<Long, CommunityCommentResponse> result = new java.util.LinkedHashMap<>();
        for (CommunityCommentEntity comment : comments) {
            UserEntity author = authors.get(comment.getAuthorId());
            result.put(comment.getId(), new CommunityCommentResponse(comment.getId(), comment.getParentCommentId(),
                    comment.getAuthorId(), author == null ? "同学" : author.getDisplayName(), avatarUrl(author),
                    comment.getContent(), signImages(JsonIds.parse(comment.getImageFileIds())),
                    signThumbnailImages(JsonIds.parse(comment.getImageFileIds())),
                    comment.getLikeCount() == null ? 0 : comment.getLikeCount(),
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
        if (user == null || user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) return null;
        try { return fileStorageService.signViewPath(Long.valueOf(user.getAvatarUrl())); }
        catch (NumberFormatException ignored) { return null; }
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

}
