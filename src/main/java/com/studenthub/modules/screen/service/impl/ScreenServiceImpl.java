package com.studenthub.modules.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityDirectMessageMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.screen.controller.dto.ScreenOverviewResponse;
import com.studenthub.modules.screen.service.ScreenService;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenServiceImpl implements ScreenService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter COMPLETED_AT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    /** 短缓存：聚合查询跨 8 张表，缓存 5 秒足以支撑现场多观众并发轮询 */
    private static final long CACHE_MILLIS = 5_000L;

    private final UserMapper userMapper;
    private final ServiceRequestMapper requestMapper;
    private final WorkOrderMapper workOrderMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityDirectMessageMapper messageMapper;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;

    private volatile ScreenOverviewResponse cachedOverview;
    private volatile long cachedAt;

    @Override
    public ScreenOverviewResponse overview() {
        long now = System.currentTimeMillis();
        ScreenOverviewResponse snapshot = cachedOverview;
        if (snapshot != null && now - cachedAt < CACHE_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = cachedOverview;
            now = System.currentTimeMillis();
            if (snapshot != null && now - cachedAt < CACHE_MILLIS) {
                return snapshot;
            }
            ScreenOverviewResponse fresh = buildOverview();
            cachedOverview = fresh;
            cachedAt = now;
            return fresh;
        }
    }

    private ScreenOverviewResponse buildOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = todayStart.minusDays(6);

        return new ScreenOverviewResponse(
                LocalDateTime.now().format(DT),
                userStats(todayStart),
                requestStats(todayStart),
                orderStats(todayStart),
                weeklyTrend(weekStart, todayStart),
                topServices(),
                communityStats(),
                aiStats(todayStart),
                recentCompleted());
    }

    private ScreenOverviewResponse.UserStats userStats(LocalDateTime todayStart) {
        List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getId, UserEntity::getIdentityType, UserEntity::getCreatedAt));
        long students = users.stream()
                .filter(u -> "STUDENT".equals(u.getIdentityType())).count();
        long todayNew = users.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(todayStart)).count();
        return new ScreenOverviewResponse.UserStats(users.size(), students, todayNew);
    }

    private ScreenOverviewResponse.RequestStats requestStats(LocalDateTime todayStart) {
        List<ServiceRequestEntity> requests = requestMapper.selectList(new LambdaQueryWrapper<ServiceRequestEntity>()
                .select(ServiceRequestEntity::getId, ServiceRequestEntity::getStatus,
                        ServiceRequestEntity::getSubmittedAt, ServiceRequestEntity::getCreatedAt));
        Map<String, Long> dist = new HashMap<>();
        long today = 0;
        for (ServiceRequestEntity request : requests) {
            dist.merge(request.getStatus(), 1L, Long::sum);
            LocalDateTime submitMark = request.getSubmittedAt() != null
                    ? request.getSubmittedAt() : request.getCreatedAt();
            if (submitMark != null && submitMark.isAfter(todayStart)) {
                today++;
            }
        }
        long inProgress = dist.getOrDefault("SUBMITTED", 0L) + dist.getOrDefault("PROCESSING", 0L);
        return new ScreenOverviewResponse.RequestStats(requests.size(), today, inProgress, dist);
    }

    private ScreenOverviewResponse.OrderStats orderStats(LocalDateTime todayStart) {
        List<WorkOrderEntity> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .select(WorkOrderEntity::getId, WorkOrderEntity::getStatus, WorkOrderEntity::getCompletedAt));
        long completed = 0;
        long processing = 0;
        long completedToday = 0;
        for (WorkOrderEntity order : orders) {
            String status = order.getStatus();
            if ("COMPLETED".equals(status)) {
                completed++;
                if (order.getCompletedAt() != null && order.getCompletedAt().isAfter(todayStart)) {
                    completedToday++;
                }
            } else if ("PENDING_DISPATCH".equals(status) || "ASSIGNED".equals(status)
                    || "PROCESSING".equals(status) || "ONSITE_PHOTO_RECORDED".equals(status)
                    || "PENDING_CONFIRMATION".equals(status)) {
                processing++;
            }
        }
        return new ScreenOverviewResponse.OrderStats(orders.size(), completed, processing, completedToday);
    }

    private List<ScreenOverviewResponse.TrendPoint> weeklyTrend(LocalDateTime weekStart, LocalDateTime todayStart) {
        List<ServiceRequestEntity> requests = requestMapper.selectList(new LambdaQueryWrapper<ServiceRequestEntity>()
                .select(ServiceRequestEntity::getId, ServiceRequestEntity::getSubmittedAt,
                        ServiceRequestEntity::getCreatedAt)
                .ge(ServiceRequestEntity::getCreatedAt, weekStart));
        List<WorkOrderEntity> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .select(WorkOrderEntity::getId, WorkOrderEntity::getCompletedAt)
                .ge(WorkOrderEntity::getCompletedAt, weekStart));

        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (ServiceRequestEntity request : requests) {
            LocalDateTime mark = request.getSubmittedAt() != null
                    ? request.getSubmittedAt() : request.getCreatedAt();
            if (mark != null && !mark.isBefore(weekStart)) {
                byDay.computeIfAbsent(mark.toLocalDate(), key -> new long[2])[0]++;
            }
        }
        for (WorkOrderEntity order : orders) {
            if (order.getCompletedAt() != null && !order.getCompletedAt().isBefore(weekStart)) {
                byDay.computeIfAbsent(order.getCompletedAt().toLocalDate(), key -> new long[2])[1]++;
            }
        }

        List<ScreenOverviewResponse.TrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.toLocalDate().plusDays(i);
            long[] counts = byDay.getOrDefault(date, new long[2]);
            trend.add(new ScreenOverviewResponse.TrendPoint(date.format(DAY),
                    WEEKDAYS[date.getDayOfWeek().getValue() - 1], counts[0], counts[1]));
        }
        return trend;
    }

    private List<ScreenOverviewResponse.ServiceHeat> topServices() {
        List<ServiceRequestEntity> requests = requestMapper.selectList(new LambdaQueryWrapper<ServiceRequestEntity>()
                .select(ServiceRequestEntity::getId, ServiceRequestEntity::getItemName));
        Map<String, Long> counts = new HashMap<>();
        for (ServiceRequestEntity request : requests) {
            if (request.getItemName() != null) {
                counts.merge(request.getItemName(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new ScreenOverviewResponse.ServiceHeat(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ScreenOverviewResponse.CommunityStats communityStats() {
        long posts = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getStatus, "ENABLED"));
        long comments = commentMapper.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getStatus, "ENABLED"));
        long messages = messageMapper.selectCount(new LambdaQueryWrapper<CommunityDirectMessageEntity>()
                .isNotNull(CommunityDirectMessageEntity::getId));
        return new ScreenOverviewResponse.CommunityStats(posts, comments, 0, messages);
    }

    private ScreenOverviewResponse.AiStats aiStats(LocalDateTime todayStart) {
        long conversations = conversationMapper.selectCount(null);
        List<AiMessageEntity> answers = aiMessageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .select(AiMessageEntity::getId, AiMessageEntity::getRole, AiMessageEntity::getCreatedAt)
                .eq(AiMessageEntity::getRole, "ASSISTANT"));
        long todayAnswers = answers.stream()
                .filter(message -> message.getCreatedAt() != null && message.getCreatedAt().isAfter(todayStart))
                .count();
        return new ScreenOverviewResponse.AiStats(conversations, answers.size(), todayAnswers);
    }

    /** 最新办结动态：仅返回服务项名称与办结时间，不含任何个人数据 */
    private List<ScreenOverviewResponse.RecentCompleted> recentCompleted() {
        List<WorkOrderEntity> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .select(WorkOrderEntity::getId, WorkOrderEntity::getTitle, WorkOrderEntity::getCompletedAt)
                .eq(WorkOrderEntity::getStatus, "COMPLETED")
                .isNotNull(WorkOrderEntity::getCompletedAt)
                .orderByDesc(WorkOrderEntity::getCompletedAt)
                .last("LIMIT 8"));
        return orders.stream()
                .map(order -> new ScreenOverviewResponse.RecentCompleted(order.getTitle(),
                        order.getCompletedAt().format(COMPLETED_AT)))
                .toList();
    }
}