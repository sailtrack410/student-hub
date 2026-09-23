package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
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
import java.util.Collections;
import java.util.List;

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
    private final ObjectMapper objectMapper;
    private final JwBindingMapper jwBindingMapper;
    private final NotificationService notificationService;
    private final CommunityModerationService communityModerationService;

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
                                                        long pageNum, long pageSize) {
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 20);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        final String search = normalizedKeyword != null && normalizedKeyword.startsWith("#")
                ? normalizedKeyword.substring(1) : normalizedKeyword;
        LambdaQueryWrapper<CommunityPostEntity> wrapper = new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, user.campusId())
                .eq(CommunityPostEntity::getStatus, ENABLED)
                .eq(sectionId != null, CommunityPostEntity::getSectionId, sectionId)
                .and(search != null && !search.isBlank(), query -> query
                        .like(CommunityPostEntity::getTitle, search)
                        .or().like(CommunityPostEntity::getContent, search)
                        .or().like(CommunityPostEntity::getTopics, search))
                .orderByDesc(CommunityPostEntity::getCreatedAt);
        Page<CommunityPostEntity> page = postMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(post -> toPostResponse(post, user.userId())).toList());
    }

    @Override
    public CommunityPostDetailResponse detail(AuthenticatedUser user, Long postId) {
        CommunityPostEntity post = requirePost(user, postId);
        List<CommunityCommentEntity> comments = commentMapper.selectList(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, postId)
                .eq(CommunityCommentEntity::getStatus, ENABLED)
                .orderByAsc(CommunityCommentEntity::getCreatedAt)
                .last("LIMIT 100"));
        return new CommunityPostDetailResponse(toPostResponse(post, user.userId()), comments.stream()
                .map(comment -> toCommentResponse(comment, user.userId(), post.getAuthorId())).toList());
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
        entity.setImageFileIds(writeIds(imageIds));
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
        entity.setImageFileIds(writeIds(imageIds));
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
        return new CommunityAuthorProfileResponse(author.getId(), author.getDisplayName(), avatarUrl(author),
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

    private CommunityPostResponse toPostResponse(CommunityPostEntity post, Long viewerId) {
        UserEntity author = userMapper.selectById(post.getAuthorId());
        CommunitySectionEntity section = sectionMapper.selectById(post.getSectionId());
        boolean liked = postLikeMapper.selectCount(new LambdaQueryWrapper<CommunityPostLikeEntity>()
                .eq(CommunityPostLikeEntity::getPostId, post.getId()).eq(CommunityPostLikeEntity::getUserId, viewerId)) > 0;
        return new CommunityPostResponse(post.getId(), post.getSectionId(), section == null ? null : section.getSectionName(),
                section == null ? null : section.getIcon(), post.getAuthorId(), author == null ? "同学" : author.getDisplayName(),
                avatarUrl(author), post.getTitle(), post.getContent(), signImages(parseIds(post.getImageFileIds())), signThumbnailImages(parseIds(post.getImageFileIds())), parseTopics(post.getTopics()),
                post.getCommentCount() == null ? 0 : post.getCommentCount(), post.getLikeCount() == null ? 0 : post.getLikeCount(), liked, post.getCreatedAt());
    }

    private CommunityCommentResponse toCommentResponse(CommunityCommentEntity comment, Long viewerId, Long postAuthorId) {
        UserEntity author = userMapper.selectById(comment.getAuthorId());
        boolean liked = commentLikeMapper.selectCount(new LambdaQueryWrapper<CommunityCommentLikeEntity>()
                .eq(CommunityCommentLikeEntity::getCommentId, comment.getId()).eq(CommunityCommentLikeEntity::getUserId, viewerId)) > 0;
        return new CommunityCommentResponse(comment.getId(), comment.getParentCommentId(), comment.getAuthorId(),
                author == null ? "同学" : author.getDisplayName(), avatarUrl(author), comment.getContent(), signImages(parseIds(comment.getImageFileIds())), signThumbnailImages(parseIds(comment.getImageFileIds())), comment.getLikeCount() == null ? 0 : comment.getLikeCount(), liked,
                comment.getPinned() != null && comment.getPinned() == 1, postAuthorId != null && postAuthorId.equals(comment.getAuthorId()), comment.getCreatedAt());
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

    private String writeIds(List<Long> ids) {
        try { return ids.isEmpty() ? null : objectMapper.writeValueAsString(ids); }
        catch (Exception exception) { throw new IllegalStateException("社区图片信息序列化失败", exception); }
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

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<Long>>() {}); }
        catch (Exception ignored) { return Collections.emptyList(); }
    }
}
