package com.studenthub.modules.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;
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
import com.studenthub.modules.screen.controller.dto.PublicChatResponse;
import com.studenthub.modules.screen.controller.dto.ScreenCatalogResponse;
import com.studenthub.modules.screen.controller.dto.ScreenNoticeResponse;
import com.studenthub.modules.screen.controller.dto.ScreenOrderProgressResponse;
import com.studenthub.modules.screen.controller.dto.ScreenOverviewResponse;
import com.studenthub.modules.screen.entity.ScreenNoticeEntity;
import com.studenthub.modules.screen.mapper.ScreenNoticeMapper;
import com.studenthub.modules.screen.service.ScreenService;
import com.studenthub.modules.servicecatalog.entity.ServiceCategoryEntity;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceCategoryMapper;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final ServiceRequestLogMapper requestLogMapper;
    private final WorkOrderMapper workOrderMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityDirectMessageMapper messageMapper;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final ServiceCategoryMapper categoryMapper;
    private final ServiceItemMapper itemMapper;
    private final AiChatService aiChatService;
    private final ScreenNoticeMapper noticeMapper;

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

    /* ==================== 互动能力 ==================== */

    @Override
    public ScreenCatalogResponse catalog() {
        List<ServiceCategoryEntity> categories = categoryMapper.selectList(new LambdaQueryWrapper<ServiceCategoryEntity>()
                .eq(ServiceCategoryEntity::getStatus, "ENABLED")
                .orderByAsc(ServiceCategoryEntity::getSortOrder));
        List<ServiceItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<ServiceItemEntity>()
                .eq(ServiceItemEntity::getStatus, "ENABLED")
                .orderByAsc(ServiceItemEntity::getSortOrder));
        Map<Long, List<ServiceItemEntity>> byCategory = new LinkedHashMap<>();
        for (ServiceItemEntity item : items) {
            byCategory.computeIfAbsent(item.getCategoryId(), key -> new ArrayList<>()).add(item);
        }
        List<ScreenCatalogResponse.Category> list = categories.stream()
                .filter(category -> byCategory.containsKey(category.getId()))
                .map(category -> {
                    List<ScreenCatalogResponse.Item> its = byCategory.get(category.getId()).stream()
                            .map(item -> new ScreenCatalogResponse.Item(item.getId(), item.getItemCode(),
                                    item.getItemName(), item.getIcon(), item.getSummary(),
                                    item.getDescription(), item.getSlaHours()))
                            .toList();
                    return new ScreenCatalogResponse.Category(category.getId(), category.getCategoryName(),
                            category.getIcon(), category.getDescription(), its);
                })
                .toList();
        return new ScreenCatalogResponse(list);
    }

    @Override
    public ScreenOrderProgressResponse track(String no) {
        if (!StringUtils.hasText(no)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请输入工单号或申请单号");
        }
        String key = no.trim().toUpperCase();
        ServiceRequestEntity request;
        if (key.startsWith("SR-")) {
            request = requestMapper.selectOne(new LambdaQueryWrapper<ServiceRequestEntity>()
                    .eq(ServiceRequestEntity::getRequestNo, key));
        } else if (key.startsWith("WO-")) {
            WorkOrderEntity order = workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrderEntity>()
                    .eq(WorkOrderEntity::getOrderNo, key));
            if (order == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未查到该工单，请核对单号");
            }
            request = requestMapper.selectById(order.getRequestId());
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "单号格式应以 SR- 或 WO- 开头");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未查到该单据，请核对单号");
        }

        String orderNo = null;
        WorkOrderEntity order = workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getRequestId, request.getId()));
        String status;
        if (order != null) {
            orderNo = order.getOrderNo();
            status = order.getStatus();
        } else {
            status = request.getStatus();
        }

        List<ScreenOrderProgressResponse.TimelinePoint> timeline = buildTimeline(request, order);
        return new ScreenOrderProgressResponse(orderNo, request.getRequestNo(), request.getItemName(),
                status, orderStatusText(status),
                request.getSubmittedAt() == null ? null : request.getSubmittedAt().format(DT),
                timeline);
    }

    /** 时间线：合并申请提交 + 工单流转日志（已脱敏，仅展示动作文案，不展示操作人姓名） */
    private List<ScreenOrderProgressResponse.TimelinePoint> buildTimeline(ServiceRequestEntity request,
                                                                          WorkOrderEntity order) {
        List<ScreenOrderProgressResponse.TimelinePoint> points = new ArrayList<>();
        if (request.getSubmittedAt() != null) {
            points.add(new ScreenOrderProgressResponse.TimelinePoint(request.getSubmittedAt().format(DT),
                    "提交申请", "申请材料已提交"));
        }
        if (order != null) {
            List<ServiceRequestLogEntity> logs = requestLogMapper.selectList(
                    new LambdaQueryWrapper<ServiceRequestLogEntity>()
                            .eq(ServiceRequestLogEntity::getRequestId, request.getId())
                            .and(w -> w.eq(ServiceRequestLogEntity::getWorkOrderId, order.getId())
                                    .or().isNull(ServiceRequestLogEntity::getWorkOrderId))
                            .orderByAsc(ServiceRequestLogEntity::getCreatedAt));
            for (ServiceRequestLogEntity entry : logs) {
                String action = timelineAction(entry.getAction());
                String note = StringUtils.hasText(entry.getComment()) ? entry.getComment() : action;
                points.add(new ScreenOrderProgressResponse.TimelinePoint(
                        entry.getCreatedAt() == null ? null : entry.getCreatedAt().format(DT),
                        action, note));
            }
        }
        return points;
    }

    private String timelineAction(String action) {
        if (action == null) {
            return "状态更新";
        }
        return switch (action) {
            case "SUBMIT" -> "提交申请";
            case "DISPATCH" -> "受理派单";
            case "START" -> "开始处理";
            case "ARRIVAL_PHOTO" -> "到达现场";
            case "COMPLETION_PHOTO" -> "处理完成";
            case "REOPEN", "REOPEN_CONFIRMATION" -> "退回补充";
            case "COMPLETE", "CONFIRM" -> "办结确认";
            case "REJECT" -> "驳回";
            case "CANCEL" -> "撤销";
            default -> action;
        };
    }

    private String orderStatusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PENDING_DISPATCH" -> "待派单";
            case "ASSIGNED" -> "已派单";
            case "PROCESSING" -> "处理中";
            case "ONSITE_PHOTO_RECORDED" -> "现场处理中";
            case "PENDING_CONFIRMATION" -> "待确认";
            case "COMPLETED" -> "已完成";
            case "REJECTED" -> "未受理";
            case "CANCELLED" -> "已撤销";
            default -> status;
        };
    }

    @Override
    public PublicChatResponse chat(String question, List<ScreenService.ChatTurn> history) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", buildPublicSystemPrompt()));
        if (history != null) {
            for (ScreenService.ChatTurn turn : history) {
                if (turn != null && StringUtils.hasText(turn.content())) {
                    String role = "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user";
                    messages.add(new AiMessage(role, turn.content().trim()));
                }
            }
        }
        messages.add(new AiMessage("user", question.trim()));
        AiChatResponse response = aiChatService.chat(messages);
        return new PublicChatResponse(response.content(), response.model());
    }

    /* ==================== 信息要闻 ==================== */

    @Override
    public ScreenNoticeResponse notices() {
        List<ScreenNoticeEntity> list = noticeMapper.selectList(new LambdaQueryWrapper<ScreenNoticeEntity>()
                .eq(ScreenNoticeEntity::getEnabled, true)
                .orderByAsc(ScreenNoticeEntity::getSortOrder)
                .orderByDesc(ScreenNoticeEntity::getPublishDate)
                .last("LIMIT 20"));
        List<ScreenNoticeResponse.Notice> notices = list.stream()
                .map(n -> new ScreenNoticeResponse.Notice(n.getNoticeType(), noticeTypeText(n.getNoticeType()),
                        n.getTitle(), n.getSummary(),
                        n.getPublishDate() == null ? null : n.getPublishDate().toString()))
                .toList();
        return new ScreenNoticeResponse(notices);
    }

    private String noticeTypeText(String type) {
        if (type == null) {
            return "通知";
        }
        return switch (type) {
            case "NEWS" -> "要闻";
            case "ACTIVITY" -> "活动";
            default -> "通知";
        };
    }

    /** 公开问答 system prompt：实时注入公开办事指南（服务目录），强调不编造 */
    private String buildPublicSystemPrompt() {
        List<ServiceCategoryEntity> categories = categoryMapper.selectList(new LambdaQueryWrapper<ServiceCategoryEntity>()
                .eq(ServiceCategoryEntity::getStatus, "ENABLED")
                .orderByAsc(ServiceCategoryEntity::getSortOrder));
        List<ServiceItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<ServiceItemEntity>()
                .eq(ServiceItemEntity::getStatus, "ENABLED")
                .orderByAsc(ServiceItemEntity::getSortOrder));
        Map<Long, String> categoryNames = new HashMap<>();
        for (ServiceCategoryEntity category : categories) {
            categoryNames.put(category.getId(), category.getCategoryName());
        }
        StringBuilder knowledge = new StringBuilder();
        for (ServiceItemEntity item : items) {
            knowledge.append("●【").append(item.getItemName()).append("】")
                    .append(StringUtils.hasText(item.getSummary()) ? item.getSummary() : "")
                    .append(item.getSlaHours() != null ? "（承诺时限" + item.getSlaHours() + "小时）" : "")
                    .append(StringUtils.hasText(item.getDescription()) ? " " + item.getDescription() : "")
                    .append("\n");
        }
        return "你是「绵城码上办」AI 校园服务终端助手，面向创意集市现场观众提供免登录公开咨询。\n"
                + "只能依据下列公开办事指南回答，不得编造任何不存在的政策、流程、电话或地点；"
                + "指南没有的信息要明确说“该问题我暂不清楚，建议联系学生事务中心”。\n"
                + "回答简洁口语化，单条不超过 100 字。引导现场观众体验办事闭环："
                + "若涉及办事，可提示其扫码进入小程序提交申请并获取单号，回到本终端查询进度。\n\n"
                + "公开办事指南：\n" + knowledge;
    }
}