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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
                // 进度未变且未新解锁时不写库：/state 会在每次启动被调用，避免无意义更新
                boolean progressChanged = !Objects.equals(record.getCurrentProgress(), value);
                boolean justUnlocked = unlocked && record.getUnlockedAt() == null;
                if (progressChanged || justUnlocked) {
                    UserAchievementBadgeEntity update = new UserAchievementBadgeEntity();
                    update.setId(record.getId());
                    if (progressChanged) {
                        update.setCurrentProgress(value);
                    }
                    if (justUnlocked) {
                        update.setUnlockedAt(LocalDateTime.now());
                    }
                    update.setVersion(record.getVersion());
                    userBadgeMapper.updateById(update);
                }
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

    /**
     * 批量总经验（2026-11-05 社区列表等级统一）：供帖子/评论批量组装下发真实等级。
     * 指标口径与 computeMetrics/totalExpOf 完全一致；每个指标一条 IN 查询 + 内存聚合，
     * 避免逐人 computeMetrics（约 10 SQL/人）在列表场景形成 N+1。
     */
    public Map<Long, Integer> totalExpBatch(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        // 帖子数与获赞和（一条查询）
        Map<Long, int[]> postStats = new java.util.HashMap<>();
        for (CommunityPostEntity p : postMapper.selectList(new LambdaQueryWrapper<CommunityPostEntity>()
                .select(CommunityPostEntity::getAuthorId, CommunityPostEntity::getLikeCount)
                .in(CommunityPostEntity::getAuthorId, ids)
                .eq(CommunityPostEntity::getStatus, "ENABLED"))) {
            int[] stat = postStats.computeIfAbsent(p.getAuthorId(), k -> new int[2]);
            stat[0]++;
            stat[1] += p.getLikeCount() == null ? 0 : p.getLikeCount();
        }

        // 评论数
        Map<Long, Integer> commentCounts = new java.util.HashMap<>();
        for (com.studenthub.modules.community.entity.CommunityCommentEntity c : commentMapper.selectList(
                new LambdaQueryWrapper<com.studenthub.modules.community.entity.CommunityCommentEntity>()
                        .select(com.studenthub.modules.community.entity.CommunityCommentEntity::getAuthorId)
                        .in(com.studenthub.modules.community.entity.CommunityCommentEntity::getAuthorId, ids)
                        .eq(com.studenthub.modules.community.entity.CommunityCommentEntity::getStatus, "ENABLED"))) {
            commentCounts.merge(c.getAuthorId(), 1, Integer::sum);
        }

        // 工单发起数与完成数（一条查询）
        Map<Long, int[]> requestStats = new java.util.HashMap<>();
        for (ServiceRequestEntity r : requestMapper.selectList(new LambdaQueryWrapper<ServiceRequestEntity>()
                .select(ServiceRequestEntity::getRequesterId, ServiceRequestEntity::getStatus)
                .in(ServiceRequestEntity::getRequesterId, ids))) {
            int[] stat = requestStats.computeIfAbsent(r.getRequesterId(), k -> new int[2]);
            stat[0]++;
            if ("COMPLETED".equals(r.getStatus())) stat[1]++;
        }

        // 评价数
        Map<Long, Integer> evalCounts = new java.util.HashMap<>();
        for (ServiceEvaluationEntity e : evaluationMapper.selectList(new LambdaQueryWrapper<ServiceEvaluationEntity>()
                .select(ServiceEvaluationEntity::getEvaluatorId)
                .in(ServiceEvaluationEntity::getEvaluatorId, ids))) {
            evalCounts.merge(e.getEvaluatorId(), 1, Integer::sum);
        }

        // 后勤完成工单
        Map<Long, Integer> workerOrders = new java.util.HashMap<>();
        for (WorkOrderEntity w : workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .select(WorkOrderEntity::getAssigneeId)
                .in(WorkOrderEntity::getAssigneeId, ids)
                .eq(WorkOrderEntity::getStatus, "COMPLETED"))) {
            workerOrders.merge(w.getAssigneeId(), 1, Integer::sum);
        }

        // 教务认证
        java.util.Set<Long> jwAuthed = jwBindingMapper.selectList(new LambdaQueryWrapper<JwBindingEntity>()
                        .select(JwBindingEntity::getUserId, JwBindingEntity::getAuthStatus)
                        .in(JwBindingEntity::getUserId, ids)).stream()
                .filter(b -> "AUTHENTICATED".equals(b.getAuthStatus()))
                .map(JwBindingEntity::getUserId)
                .collect(java.util.stream.Collectors.toSet());

        // 课表导入（仅已认证者才可能 >0）
        java.util.Set<Long> scheduleImported = jwAuthed.isEmpty() ? java.util.Set.of()
                : scheduleCourseMapper.selectList(new LambdaQueryWrapper<ScheduleCourseEntity>()
                        .select(ScheduleCourseEntity::getUserId)
                        .in(ScheduleCourseEntity::getUserId, jwAuthed)).stream()
                .map(ScheduleCourseEntity::getUserId)
                .collect(java.util.stream.Collectors.toSet());

        // 资料完整度（头像 + 手机号）
        java.util.Set<Long> profileComplete = userMapper.selectBatchIds(ids).stream()
                .filter(u -> u.getAvatarUrl() != null && !u.getAvatarUrl().isBlank()
                        && u.getPhone() != null && !u.getPhone().isBlank())
                .map(UserEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, Integer> result = new java.util.HashMap<>();
        for (Long id : ids) {
            int[] post = postStats.get(id);
            int[] request = requestStats.get(id);
            int totalExp = (post == null ? 0 : post[0]) * 5
                    + commentCounts.getOrDefault(id, 0) * 2
                    + (post == null ? 0 : post[1])
                    + (request == null ? 0 : request[0]) * 3
                    + (request == null ? 0 : request[1]) * 10
                    + evalCounts.getOrDefault(id, 0) * 5
                    + workerOrders.getOrDefault(id, 0) * 12
                    + (jwAuthed.contains(id) ? 20 : 0)
                    + (scheduleImported.contains(id) ? 5 : 0)
                    + (profileComplete.contains(id) ? 20 : 0);
            result.put(id, totalExp);
        }
        return result;
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

    /** 佩戴：卸下传 null；佩戴须已解锁且勋章启用（旧签名保留兼容）。 */
    public void wear(Long userId, String badgeCode) {
        wear(userId, badgeCode, null);
    }

    /**
     * 佩戴/卸下：action=WEAR/UNWEAR；缺省时 badgeCode 非空=佩戴、为空=卸下（兼容只传 badgeCode 的旧客户端）。
     * 佩戴校验：勋章存在、启用、本人已解锁。
     */
    public void wear(Long userId, String badgeCode, String action) {
        String normalizedAction = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        boolean actionPresent = normalizedAction != null && !normalizedAction.isBlank();
        if (actionPresent && !"WEAR".equals(normalizedAction) && !"UNWEAR".equals(normalizedAction)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "action 仅支持 WEAR 或 UNWEAR");
        }
        String code = badgeCode == null || badgeCode.isBlank() ? null : badgeCode.trim();
        boolean unwear = "UNWEAR".equals(normalizedAction) || (!actionPresent && code == null);
        if (unwear) {
            updateWornBadge(userId, null);
            return;
        }
        if (code == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "佩戴勋章时 badgeCode 不能为空");
        }
        AchievementBadgeEntity badge = badgeMapper.selectOne(
                new LambdaQueryWrapper<AchievementBadgeEntity>()
                        .eq(AchievementBadgeEntity::getBadgeCode, code));
        if (badge == null || !Boolean.TRUE.equals(badge.getEnabled())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该勋章不存在或已停用，无法佩戴");
        }
        if (!unlocked(userId, code)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "尚未获得该勋章，无法佩戴");
        }
        updateWornBadge(userId, code);
    }

    private void updateWornBadge(Long userId, String badgeCode) {
        UserEntity update = new UserEntity();
        update.setId(userId);
        update.setWornBadgeCode(badgeCode);
        UserEntity current = userMapper.selectById(userId);
        update.setVersion(current != null ? current.getVersion() : null);
        userMapper.updateById(update);
    }
}
