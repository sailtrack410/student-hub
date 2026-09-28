package com.studenthub.modules.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;
import com.studenthub.modules.schedule.mapper.ScheduleCourseMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 成就评估（2026-10-17 定稿方案）：经验不落流水、每次从业务表现状重算（零侵入、天然幂等、删帖掉经验即防刷）；
 * 授予幂等由 (user_id, badge_code) 唯一键保证；解锁发站内信（notified 标记防重复）。
 * 触发时机：成长页/个人中心打开即评估（懒评估），本期服务申请/评价/教务模块不加即时钩子。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    /** 等级曲线（1-9 级封顶）：累计经验下限 */
    private static final int[] LEVEL_EXP = {0, 50, 150, 350, 700, 1200, 2000, 3200, 5000};
    private static final String[] LEVEL_NAMES = {
            "初来乍到", "校园新人", "活跃同学", "热心同学", "校园骨干", "校园达人", "校园之星", "校园榜样", "校园传奇"};

    private final AchievementBadgeMapper badgeMapper;
    private final UserAchievementBadgeMapper userBadgeMapper;
    private final UserMapper userMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final ServiceRequestMapper requestMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final JwBindingMapper jwBindingMapper;
    private final ScheduleCourseMapper scheduleCourseMapper;
    private final NotificationService notificationService;

    /** 懒评估入口：重算全部指标→刷新进度→授予解锁→发通知。返回本次新解锁数。 */
    @Transactional(rollbackFor = Exception.class)
    public int evaluate(Long userId) {
        Map<String, Integer> metrics = computeMetrics(userId);
        List<AchievementBadgeEntity> badges = badgeMapper.selectList(
                new LambdaQueryWrapper<AchievementBadgeEntity>().eq(AchievementBadgeEntity::getEnabled, true));
        int newlyUnlocked = 0;
        for (AchievementBadgeEntity badge : badges) {
            Integer value = metrics.get(badge.getMetricCode());
            if (value == null) {
                continue;
            }
            UserAchievementBadgeEntity record = userBadgeMapper.selectOne(
                    new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                            .eq(UserAchievementBadgeEntity::getUserId, userId)
                            .eq(UserAchievementBadgeEntity::getBadgeCode, badge.getBadgeCode()));
            boolean unlocked = value >= badge.getMetricThreshold();
            if (record == null) {
                record = new UserAchievementBadgeEntity();
                record.setUserId(userId);
                record.setBadgeCode(badge.getBadgeCode());
                record.setCurrentProgress(value);
                record.setUnlockedAt(unlocked ? LocalDateTime.now() : null);
                record.setNotified(false);
                userBadgeMapper.insert(record);
            } else {
                UserAchievementBadgeEntity update = new UserAchievementBadgeEntity();
                update.setId(record.getId());
                update.setCurrentProgress(value);
                update.setVersion(record.getVersion());
                if (unlocked && record.getUnlockedAt() == null) {
                    update.setUnlockedAt(LocalDateTime.now());
                }
                userBadgeMapper.updateById(update);
            }
            if (unlocked && (record.getUnlockedAt() == null || !Boolean.TRUE.equals(record.getNotified()))) {
                newlyUnlocked++;
                notifyUnlock(userId, badge);
            }
        }
        return newlyUnlocked;
    }

    /** 全部指标从业务表现状重算；获赞指标排除自赞（决策点 4） */
    private Map<String, Integer> computeMetrics(Long userId) {
        Long postCount = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getAuthorId, userId)
                .eq(CommunityPostEntity::getStatus, "ENABLED"));
        Long commentCount = commentMapper.selectCount(new LambdaQueryWrapper<com.studenthub.modules.community.entity.CommunityCommentEntity>()
                .eq(com.studenthub.modules.community.entity.CommunityCommentEntity::getAuthorId, userId)
                .eq(com.studenthub.modules.community.entity.CommunityCommentEntity::getStatus, "ENABLED"));
        // 获赞总数：本人帖子的 like_count 之和（点赞表计数列，取消赞自动回落=经验可回收）
        List<CommunityPostEntity> myPosts = postMapper.selectList(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getAuthorId, userId)
                .eq(CommunityPostEntity::getStatus, "ENABLED")
                .select(CommunityPostEntity::getLikeCount));
        int postLikes = myPosts.stream().mapToInt(p -> p.getLikeCount() == null ? 0 : p.getLikeCount()).sum();
        int singlePostLikes = myPosts.stream().mapToInt(p -> p.getLikeCount() == null ? 0 : p.getLikeCount()).max().orElse(0);

        Long requestCount = requestMapper.selectCount(new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getRequesterId, userId));
        Long requestCompleted = requestMapper.selectCount(new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getRequesterId, userId)
                .eq(ServiceRequestEntity::getStatus, "COMPLETED"));
        Long evaluationCount = evaluationMapper.selectCount(new LambdaQueryWrapper<ServiceEvaluationEntity>()
                .eq(ServiceEvaluationEntity::getEvaluatorId, userId));
        Long fiveStarCount = evaluationMapper.selectCount(new LambdaQueryWrapper<ServiceEvaluationEntity>()
                .eq(ServiceEvaluationEntity::getEvaluatorId, userId)
                .eq(ServiceEvaluationEntity::getRating, 5));
        Long workerOrders = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getAssigneeId, userId)
                .eq(WorkOrderEntity::getStatus, "COMPLETED"));

        JwBindingEntity binding = jwBindingMapper.findByUserId(userId);
        int jwAuth = binding != null && "AUTHENTICATED".equals(binding.getAuthStatus()) ? 1 : 0;
        Long scheduleImported = jwAuth == 1
                ? scheduleCourseMapper.selectCount(new LambdaQueryWrapper<ScheduleCourseEntity>()
                        .eq(ScheduleCourseEntity::getUserId, userId))
                : 0;
        UserEntity user = userMapper.selectById(userId);
        int profileComplete = user != null
                && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()
                && user.getPhone() != null && !user.getPhone().isBlank() ? 1 : 0;

        int totalExp = (int) (postCount * 5 + commentCount * 2 + postLikes + requestCount * 3
                + requestCompleted * 10 + evaluationCount * 5 + workerOrders * 12
                + jwAuth * 20 + (scheduleImported > 0 ? 5 : 0) + profileComplete * 20);
        int level = levelOf(totalExp);

        return Map.ofEntries(
                Map.entry("POST_COUNT", postCount.intValue()),
                Map.entry("COMMENT_COUNT", commentCount.intValue()),
                Map.entry("POST_LIKES", postLikes),
                Map.entry("POST_LIKES_SINGLE", singlePostLikes),
                Map.entry("REQUEST_COUNT", requestCount.intValue()),
                Map.entry("REQUEST_COMPLETED", requestCompleted.intValue()),
                Map.entry("EVALUATION_COUNT", evaluationCount.intValue()),
                Map.entry("EVALUATION_FIVE_STAR", fiveStarCount.intValue()),
                Map.entry("WORKER_ORDER_COMPLETED", workerOrders.intValue()),
                Map.entry("JW_AUTH", jwAuth),
                Map.entry("SCHEDULE_IMPORTED", scheduleImported.intValue()),
                Map.entry("PROFILE_COMPLETE", profileComplete),
                Map.entry("LEVEL", level));
    }

    /** 累计经验 → 等级（1-9 封顶） */
    public static int levelOf(int totalExp) {
        int level = 1;
        for (int i = LEVEL_EXP.length - 1; i >= 0; i--) {
            if (totalExp >= LEVEL_EXP[i]) {
                level = i + 1;
                break;
            }
        }
        return level;
    }

    /** 指标重算单点：profile 展示与评估共用同一口径 */
    public int totalExpOf(Long userId) {
        Map<String, Integer> metrics = computeMetrics(userId);
        return metrics.get("POST_COUNT") * 5 + metrics.get("COMMENT_COUNT") * 2 + metrics.get("POST_LIKES")
                + metrics.get("REQUEST_COUNT") * 3 + metrics.get("REQUEST_COMPLETED") * 10
                + metrics.get("EVALUATION_COUNT") * 5 + metrics.get("WORKER_ORDER_COMPLETED") * 12
                + metrics.get("JW_AUTH") * 20 + (metrics.get("SCHEDULE_IMPORTED") > 0 ? 5 : 0)
                + metrics.get("PROFILE_COMPLETE") * 20;
    }

    public static int maxLevel() {
        return LEVEL_EXP.length;
    }

    /** level 的累计经验下限（level 从 1 起） */
    public static int levelExp(int level) {
        return LEVEL_EXP[Math.min(Math.max(level, 1), LEVEL_EXP.length) - 1];
    }

    public static String levelName(int level) {
        return LEVEL_NAMES[Math.min(Math.max(level, 1), LEVEL_NAMES.length) - 1];
    }

    private void notifyUnlock(Long userId, AchievementBadgeEntity badge) {
        try {
            UserAchievementBadgeEntity mark = userBadgeMapper.selectOne(
                    new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                            .eq(UserAchievementBadgeEntity::getUserId, userId)
                            .eq(UserAchievementBadgeEntity::getBadgeCode, badge.getBadgeCode()));
            notificationService.send(new NotificationSendCommand(
                    userId, "SYSTEM", "获得新勋章",
                    "恭喜获得「" + badge.getBadgeName() + "」：" + badge.getDescription()
                            + (badge.getRewardExp() > 0 ? "，经验 +" + badge.getRewardExp() : ""),
                    "ACHIEVEMENT", mark != null ? mark.getId() : null));
            if (mark != null) {
                UserAchievementBadgeEntity update = new UserAchievementBadgeEntity();
                update.setId(mark.getId());
                update.setNotified(true);
                update.setVersion(mark.getVersion());
                userBadgeMapper.updateById(update);
            }
        } catch (Exception exception) {
            log.warn("勋章解锁通知发送失败, userId={}, badge={}", userId, badge.getBadgeCode());
        }
    }

    /** 佩戴校验用：确认该勋章已解锁 */
    public boolean unlocked(Long userId, String badgeCode) {
        UserAchievementBadgeEntity record = userBadgeMapper.selectOne(
                new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                        .eq(UserAchievementBadgeEntity::getUserId, userId)
                        .eq(UserAchievementBadgeEntity::getBadgeCode, badgeCode));
        return record != null && record.getUnlockedAt() != null;
    }

    /** 佩戴：卸下传 null；佩戴须已解锁 */
    public void wear(Long userId, String badgeCode) {
        if (badgeCode != null && !unlocked(userId, badgeCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "尚未获得该勋章，无法佩戴");
        }
        UserEntity update = new UserEntity();
        update.setId(userId);
        update.setWornBadgeCode(badgeCode);
        UserEntity current = userMapper.selectById(userId);
        update.setVersion(current != null ? current.getVersion() : null);
        userMapper.updateById(update);
    }
}
