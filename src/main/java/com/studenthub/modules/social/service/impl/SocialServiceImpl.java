package com.studenthub.modules.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.entity.CommunityCommentLikeEntity;
import com.studenthub.modules.community.entity.CommunityPostLikeEntity;
import com.studenthub.modules.community.mapper.CommunityCommentLikeMapper;
import com.studenthub.modules.community.mapper.CommunityPostLikeMapper;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.achievement.service.AchievementService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.social.controller.dto.FollowResponse;
import com.studenthub.modules.social.controller.dto.PrivacySettingsResponse;
import com.studenthub.modules.social.controller.dto.SocialStatsResponse;
import com.studenthub.modules.social.controller.dto.UserCardResponse;
import com.studenthub.modules.social.entity.UserFollowEntity;
import com.studenthub.modules.social.entity.UserPrivacyEntity;
import com.studenthub.modules.social.mapper.UserFollowMapper;
import com.studenthub.modules.social.mapper.UserPrivacyMapper;
import com.studenthub.modules.social.service.SocialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社交关系实现：关注唯一对（重复关注幂等返回当前态）；隐私缺行=全公开（读不插行）。
 * 名片为他人视角拼装：经验实时重算、勋章预览受 showMedals 控制、学业字段受 showAcademicInfo 控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialServiceImpl implements SocialService {

    private static final int BADGE_PREVIEW_LIMIT = 4;

    private final UserFollowMapper followMapper;
    private final UserPrivacyMapper privacyMapper;
    private final UserMapper userMapper;
    private final JwBindingMapper jwBindingMapper;
    private final AchievementService achievementService;
    private final AchievementBadgeMapper badgeMapper;
    private final UserAchievementBadgeMapper userBadgeMapper;
    private final CommunityPostLikeMapper postLikeMapper;
    private final CommunityCommentLikeMapper commentLikeMapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResponse follow(AuthenticatedUser user, Long targetUserId) {
        if (user.userId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不能关注自己");
        }
        UserEntity target = requireUser(targetUserId);
        boolean exists = followMapper.countByPair(user.userId(), targetUserId) > 0;
        if (!exists) {
            UserFollowEntity entity = new UserFollowEntity();
            entity.setCampusId(target.getCampusId());
            entity.setFollowerId(user.userId());
            entity.setFolloweeId(targetUserId);
            try {
                followMapper.insert(entity);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 并发重复关注：唯一索引兜底，按幂等处理
            }
        }
        return currentFollowState(user.userId(), targetUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResponse unfollow(AuthenticatedUser user, Long targetUserId) {
        requireUser(targetUserId);
        followMapper.deleteByPair(user.userId(), targetUserId);
        return currentFollowState(user.userId(), targetUserId);
    }

    @Override
    public UserCardResponse card(AuthenticatedUser viewer, Long targetUserId) {
        UserEntity target = requireUser(targetUserId);
        boolean following = followMapper.countByPair(viewer.userId(), targetUserId) > 0;
        boolean mutual = following && followMapper.countByPair(targetUserId, viewer.userId()) > 0;

        int totalExp = achievementService.totalExpOf(targetUserId);
        int level = AchievementService.levelOf(totalExp);
        Integer nextLevelExp = level < AchievementService.maxLevel() ? AchievementService.levelExp(level + 1) : null;

        List<UserAchievementBadgeEntity> unlocked = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                        .eq(UserAchievementBadgeEntity::getUserId, targetUserId)
                        .isNotNull(UserAchievementBadgeEntity::getUnlockedAt));
        // 只统计启用勋章（方案B：停用/删除的勋章不计入已获得数量与名片预览）
        List<AchievementBadgeEntity> enabledBadges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .eq(AchievementBadgeEntity::getEnabled, true));
        Map<String, AchievementBadgeEntity> enabledByCode = enabledBadges.stream()
                .collect(Collectors.toMap(AchievementBadgeEntity::getBadgeCode, Function.identity(),
                        (left, right) -> left));
        List<UserAchievementBadgeEntity> enabledUnlocked = unlocked.stream()
                .filter(record -> enabledByCode.containsKey(record.getBadgeCode()))
                .toList();
        long totalBadge = enabledBadges.size();
        List<UserCardResponse.BadgePreview> previews = showMedals(targetUserId)
                ? enabledUnlocked.stream()
                        .sorted(Comparator.comparing(UserAchievementBadgeEntity::getUnlockedAt).reversed())
                        .limit(BADGE_PREVIEW_LIMIT)
                        .map(record -> {
                            AchievementBadgeEntity badge = enabledByCode.get(record.getBadgeCode());
                            return new UserCardResponse.BadgePreview(record.getBadgeCode(),
                                    badge.getBadgeName(), badge.getIconUrl());
                        }).toList()
                : List.of();

        return new UserCardResponse(target.getId(), target.getUsername(), target.getDisplayName(),
                avatarUrl(target), target.getGender(), target.getIdentityType(),
                Boolean.TRUE.equals(target.getOfficial()), target.getOfficialTitle(),
                level, AchievementService.levelName(level), totalExp, nextLevelExp,
                following, mutual, enabledUnlocked.size(), totalBadge, previews);
    }

    @Override
    public SocialStatsResponse stats(AuthenticatedUser user) {
        Long userId = user.userId();
        long following = followMapper.selectCount(new LambdaQueryWrapper<UserFollowEntity>()
                .eq(UserFollowEntity::getFollowerId, userId));
        long followers = followMapper.selectCount(new LambdaQueryWrapper<UserFollowEntity>()
                .eq(UserFollowEntity::getFolloweeId, userId));
        long mutual = followMapper.selectMutualFolloweeIds(userId).size();
        // 获赞只计有效内容（ENABLED/DELETED 均不算，与信息流口径一致）
        long likes = postLikeMapper.selectCount(new LambdaQueryWrapper<CommunityPostLikeEntity>()
                .inSql(CommunityPostLikeEntity::getPostId,
                        "SELECT id FROM community_post WHERE author_id = " + userId + " AND status = 'ENABLED'"))
                + commentLikeMapper.selectCount(new LambdaQueryWrapper<CommunityCommentLikeEntity>()
                .inSql(CommunityCommentLikeEntity::getCommentId,
                        "SELECT id FROM community_comment WHERE author_id = " + userId + " AND status = 'ENABLED'"));
        return new SocialStatsResponse(likes, mutual, following, followers);
    }

    @Override
    public PrivacySettingsResponse privacyOf(Long userId) {
        UserPrivacyEntity entity = privacyMapper.selectById(userId);
        if (entity == null) {
            return new PrivacySettingsResponse(true, true);
        }
        return new PrivacySettingsResponse(!Boolean.FALSE.equals(entity.getShowAcademicInfo()),
                !Boolean.FALSE.equals(entity.getShowMedals()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivacySettingsResponse updatePrivacy(AuthenticatedUser user, PrivacySettingsResponse settings) {
        UserPrivacyEntity entity = privacyMapper.selectById(user.userId());
        if (entity == null) {
            entity = new UserPrivacyEntity();
            entity.setUserId(user.userId());
        }
        entity.setShowAcademicInfo(settings.showAcademicInfo());
        entity.setShowMedals(settings.showMedals());
        entity.setUpdatedAt(LocalDateTime.now());
        privacyMapper.insertOrUpdate(entity);
        log.info("隐私设置已更新, userId={}, showAcademicInfo={}, showMedals={}",
                user.userId(), settings.showAcademicInfo(), settings.showMedals());
        return settings;
    }

    @Override
    public boolean showAcademicInfo(Long userId) {
        return privacyOf(userId).showAcademicInfo();
    }

    @Override
    public boolean showMedals(Long userId) {
        return privacyOf(userId).showMedals();
    }

    @Override
    public boolean isFollowing(Long viewerId, Long targetId) {
        return followMapper.countByPair(viewerId, targetId) > 0;
    }

    private FollowResponse currentFollowState(Long viewerId, Long targetUserId) {
        boolean following = followMapper.countByPair(viewerId, targetUserId) > 0;
        boolean mutual = following && followMapper.countByPair(targetUserId, viewerId) > 0;
        return new FollowResponse(targetUserId, following, mutual);
    }

    private UserEntity requireUser(Long userId) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return entity;
    }

    private String avatarUrl(UserEntity user) {
        return user == null ? null : fileStorageService.resolveAvatarUrl(user.getAvatarUrl());
    }
}
