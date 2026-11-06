package com.studenthub.modules.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.community.entity.CommunityAppealEntity;
import com.studenthub.modules.community.entity.CommunityReportEntity;
import com.studenthub.modules.community.mapper.CommunityAppealMapper;
import com.studenthub.modules.community.mapper.CommunityReportMapper;
import com.studenthub.modules.dashboard.controller.dto.DashboardResponse;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.screen.entity.ScreenNoticeEntity;
import com.studenthub.modules.screen.mapper.ScreenNoticeMapper;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端工作台聚合服务：KPI、今日待办、快捷入口、日期范围趋势与最新工单，全部来自真实业务表。
 * 数据范围：当前管理端校区上下文。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("M月d日");
    private static final int MAX_RANGE_DAYS = 31;

    private final WorkOrderMapper workOrderMapper;
    private final ServiceRequestMapper serviceRequestMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final AiConversationMapper aiConversationMapper;
    private final UserMapper userMapper;
    private final CommunityReportMapper communityReportMapper;
    private final CommunityAppealMapper communityAppealMapper;
    private final ScreenNoticeMapper screenNoticeMapper;
    private final AdminCampusContextService campusContextService;
    private final WorkingHoursCalculator workingHoursCalculator;

    public DashboardResponse overview(LocalDate startDate, LocalDate endDate) {
        Long campusId = campusContextService.currentCampusId();
        LocalDate end = endDate == null ? LocalDate.now(ZONE) : endDate;
        LocalDate start = startDate == null ? end.minusDays(6) : startDate;
        validateRange(start, end);
        int days = (int) (end.toEpochDay() - start.toEpochDay()) + 1;
        LocalDateTime todayStart = LocalDate.now(ZONE).atStartOfDay();
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();
        LocalDateTime prevStart = rangeStart.minusDays(days);
        LocalDateTime prevEnd = rangeStart;

        // ---- KPI：待处理工单（进行中）与同长度上一周期创建量对比 ----
        long pendingNow = workOrderMapper.selectCount(wrapper()
                .in("status", WorkOrderTransitions.inProgressSet()));
        long pendingPrev = countCreatedBetween(prevStart, prevEnd,
                w -> w.in("status", WorkOrderTransitions.inProgressSet()));
        double pendingDelta = percentDelta(pendingNow, pendingPrev);

        // ---- KPI：今日新增用户（对比昨日）----
        long todayNewUsers = userMapper.selectCount(new QueryWrapper<UserEntity>()
                .eq("deleted", 0).eq("campus_id", campusId)
                .ge("created_at", todayStart).lt("created_at", todayStart.plusDays(1)));
        long yesterdayNewUsers = userMapper.selectCount(new QueryWrapper<UserEntity>()
                .eq("deleted", 0).eq("campus_id", campusId)
                .ge("created_at", todayStart.minusDays(1)).lt("created_at", todayStart));
        double todayNewUsersDelta = percentDelta(todayNewUsers, yesterdayNewUsers);

        // ---- KPI：今日 AI 会话量（对比昨日）----
        long aiToday = countAiBetween(todayStart, todayStart.plusDays(1));
        long aiYesterday = countAiBetween(todayStart.minusDays(1), todayStart);
        double aiDelta = percentDelta(aiToday, aiYesterday);

        // ---- 今日待办汇总 ----
        long pendingRequests = serviceRequestMapper.selectCount(reqWrapper()
                .eq("status", "SUBMITTED"));
        long pendingReports = communityReportMapper.selectCount(new QueryWrapper<CommunityReportEntity>()
                .eq("deleted", 0).eq("campus_id", campusId).eq("status", "PENDING"));
        long pendingAppeals = communityAppealMapper.selectCount(new QueryWrapper<CommunityAppealEntity>()
                .eq("deleted", 0).eq("campus_id", campusId).eq("status", "PENDING"));
        long draftNotices = screenNoticeMapper.selectCount(new QueryWrapper<ScreenNoticeEntity>()
                .eq("publish_status", "DRAFT"));
        long scheduledNotices = screenNoticeMapper.selectCount(new QueryWrapper<ScreenNoticeEntity>()
                .eq("publish_status", "SCHEDULED"));
        long overdueWorkOrders = countOverdueWorkOrders(campusId);

        List<DashboardResponse.TodoItem> todos = List.of(
                new DashboardResponse.TodoItem("pendingRequests", "待审服务申请", pendingRequests,
                        "requests", "service-request:view:self", "warn"),
                new DashboardResponse.TodoItem("overdueWorkOrders", "超时工单", overdueWorkOrders,
                        "workorders", "work-order:dispatch", "bad"),
                new DashboardResponse.TodoItem("pendingReports", "待处理举报", pendingReports,
                        "community", "community:moderate", "bad"),
                new DashboardResponse.TodoItem("pendingAppeals", "待复核申诉", pendingAppeals,
                        "community-appeals", "community:moderate", "warn"),
                new DashboardResponse.TodoItem("draftNotices", "待发公告草稿", draftNotices,
                        "notices", "announcement:manage", "muted"),
                new DashboardResponse.TodoItem("scheduledNotices", "定时公告", scheduledNotices,
                        "notices", "announcement:manage", "info"));
        long todoTotal = pendingRequests + overdueWorkOrders + pendingReports + pendingAppeals
                + draftNotices + scheduledNotices;

        // ---- 日期范围内趋势（按天统计创建/办结）----
        List<DashboardResponse.TrendPoint> trend = new ArrayList<>();
        long rangeCreated = 0;
        long rangeResolved = 0;
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long created = countCreatedBetween(dayStart, dayEnd, w -> { });
            long resolved = workOrderMapper.selectCount(wrapper()
                    .ge("completed_at", dayStart).lt("completed_at", dayEnd));
            trend.add(new DashboardResponse.TrendPoint(day.format(DAY_LABEL), created, resolved));
            rangeCreated += created;
            rangeResolved += resolved;
        }
        double resolveRate = rangeCreated == 0 ? 0 : Math.round(rangeResolved * 1000.0 / rangeCreated) / 10.0;
        long dailyAvg = Math.round(rangeCreated / (double) days);

        // ---- 分类占比（范围内工单按关联服务项聚合，无服务项归"其他"）----
        Map<String, Long> byItem = new LinkedHashMap<>();
        List<WorkOrderEntity> rangeOrders = workOrderMapper.selectList(wrapper()
                .ge("created_at", rangeStart).lt("created_at", rangeEnd));
        Map<Long, String> rangeItemNames = rangeOrders.isEmpty() ? Map.of()
                : serviceItemMapper.selectBatchIds(rangeOrders.stream().map(WorkOrderEntity::getItemId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ServiceItemEntity::getId, ServiceItemEntity::getItemName));
        for (WorkOrderEntity order : rangeOrders) {
            String key = order.getItemId() == null ? "其他"
                    : rangeItemNames.getOrDefault(order.getItemId(), "服务 #" + order.getItemId());
            byItem.merge(key, 1L, Long::sum);
        }
        long totalOrders = byItem.values().stream().mapToLong(Long::longValue).sum();
        List<DashboardResponse.CategorySlice> categories = byItem.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(4)
                .map(e -> new DashboardResponse.CategorySlice(
                        e.getKey(), totalOrders == 0 ? 0 : Math.round(e.getValue() * 1000.0 / totalOrders) / 10.0))
                .toList();

        // ---- 最新工单 5 条 ----
        List<WorkOrderEntity> latest = workOrderMapper.selectList(wrapper()
                .orderByDesc("created_at").last("LIMIT 5"));
        List<DashboardResponse.LatestOrder> latestOrders = latest.stream()
                .map(o -> new DashboardResponse.LatestOrder(o.getId(), o.getOrderNo(), o.getTitle(),
                        o.getStatus(), o.getCreatedAt() == null ? null : o.getCreatedAt().toString()))
                .toList();

        return new DashboardResponse(
                pendingNow, pendingDelta,
                todayNewUsers, todayNewUsersDelta,
                aiToday, aiDelta,
                pendingRequests, 0,
                rangeCreated, rangeResolved, resolveRate, dailyAvg,
                start.toString(), end.toString(), todoTotal, todos, quickEntries(),
                trend, categories, latestOrders);
    }

    private List<DashboardResponse.QuickEntry> quickEntries() {
        return List.of(
                new DashboardResponse.QuickEntry("workorders", "工单调度", "workorders", "work-order:dispatch"),
                new DashboardResponse.QuickEntry("requests", "服务申请", "requests", "service-request:view:self"),
                new DashboardResponse.QuickEntry("community", "举报处置", "community", "community:moderate"),
                new DashboardResponse.QuickEntry("community-appeals", "申诉复核", "community-appeals", "community:moderate"),
                new DashboardResponse.QuickEntry("notices", "通知公告", "notices", "announcement:manage"),
                new DashboardResponse.QuickEntry("analytics", "数据分析", "analytics", "analytics:view"),
                new DashboardResponse.QuickEntry("sla", "SLA 看板", "sla", "sla:view"),
                new DashboardResponse.QuickEntry("users", "用户管理", "users", "system:user:manage"),
                new DashboardResponse.QuickEntry("system-config", "系统配置", "system-config", "system:config:view"),
                new DashboardResponse.QuickEntry("audit", "审计日志", "audit", "system:audit:view"));
    }

    /** 进行中工单里，按服务承诺 + 工作日历推算已超过截止时间的数量。 */
    private long countOverdueWorkOrders(Long campusId) {
        List<WorkOrderEntity> orders = workOrderMapper.selectList(wrapper()
                .in("status", WorkOrderTransitions.inProgressSet()));
        if (orders.isEmpty()) {
            return 0;
        }
        Set<Long> requestIds = orders.stream().map(WorkOrderEntity::getRequestId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ServiceRequestEntity> requests = requestIds.isEmpty() ? Map.of()
                : serviceRequestMapper.selectBatchIds(requestIds).stream()
                .collect(Collectors.toMap(ServiceRequestEntity::getId, r -> r));
        Set<Long> itemIds = orders.stream().map(WorkOrderEntity::getItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ServiceItemEntity> items = itemIds.isEmpty() ? Map.of()
                : serviceItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(ServiceItemEntity::getId, i -> i));
        WorkingHoursCalculator.WorkCalendar calendar = workingHoursCalculator.calendar(campusId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        long overdue = 0;
        for (WorkOrderEntity order : orders) {
            ServiceRequestEntity request = requests.get(order.getRequestId());
            ServiceItemEntity item = items.get(order.getItemId());
            if (request == null || item == null || item.getSlaHours() == null || item.getSlaHours() <= 0) {
                continue;
            }
            LocalDateTime submitted = request.getSubmittedAt() != null
                    ? request.getSubmittedAt() : request.getCreatedAt();
            LocalDateTime deadline = workingHoursCalculator.deadline(submitted, item.getSlaHours(), calendar);
            if (deadline != null && now.isAfter(deadline)) {
                overdue++;
            }
        }
        return overdue;
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "开始日期不能晚于结束日期");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工作台统计区间最长 31 天");
        }
    }

    private QueryWrapper<WorkOrderEntity> wrapper() {
        return new QueryWrapper<WorkOrderEntity>().eq("deleted", 0)
                .eq("flow_archived", false)
                .inSql("request_id", WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq("campus_id", campusContextService.currentCampusId());
    }

    private QueryWrapper<ServiceRequestEntity> reqWrapper() {
        return new QueryWrapper<ServiceRequestEntity>().eq("deleted", 0)
                .eq("campus_id", campusContextService.currentCampusId());
    }

    private long countCreatedBetween(LocalDateTime start, LocalDateTime end,
                                     java.util.function.Consumer<QueryWrapper<WorkOrderEntity>> extra) {
        QueryWrapper<WorkOrderEntity> wrapper = wrapper().ge("created_at", start).lt("created_at", end);
        extra.accept(wrapper);
        return workOrderMapper.selectCount(wrapper);
    }

    private long countAiBetween(LocalDateTime start, LocalDateTime end) {
        return aiConversationMapper.selectCount(
                new QueryWrapper<AiConversationEntity>().ge("created_at", start).lt("created_at", end)
                        .inSql("user_id", "SELECT id FROM sys_user WHERE deleted = 0 AND campus_id = "
                                + campusContextService.currentCampusId()));
    }

    private double percentDelta(long now, long before) {
        if (before == 0) {
            return now == 0 ? 0 : 100.0;
        }
        return Math.round((now - before) * 1000.0 / before) / 10.0;
    }
}
