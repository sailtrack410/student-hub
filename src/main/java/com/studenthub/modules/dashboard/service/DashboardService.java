package com.studenthub.modules.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.studenthub.modules.dashboard.controller.dto.DashboardResponse;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端工作台聚合服务：全部数字来自真实业务表。
 * 数据范围：全平台（管理端概览视角）。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("M月d日");

    private final WorkOrderMapper workOrderMapper;
    private final ServiceRequestMapper serviceRequestMapper;
    private final AiConversationMapper aiConversationMapper;
    private final UserMapper userMapper;

    public DashboardResponse overview() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime prevWeekStart = today.minusDays(13).atStartOfDay();

        // ---- KPI: 待处理工单（待派发+已派发+处理中），对比上周同期 ----
        long pendingNow = workOrderMapper.selectCount(wrapper()
                .in("status", WorkOrderTransitions.inProgressSet()));
        long pendingPrev = countCreatedBetween(prevWeekStart, todayStart.minusDays(7),
                w -> w.in("status", WorkOrderTransitions.inProgressSet()));
        double pendingDelta = percentDelta(pendingNow, pendingPrev);

        // ---- KPI: 今日新增用户（identity 用户表当日创建数，对比昨日）----
        long todayNewUsers = userMapper.selectCount(new QueryWrapper<UserEntity>()
                .eq("deleted", 0).ge("created_at", todayStart).lt("created_at", todayStart.plusDays(1)));
        long yesterdayNewUsers = userMapper.selectCount(new QueryWrapper<UserEntity>()
                .eq("deleted", 0).ge("created_at", todayStart.minusDays(1)).lt("created_at", todayStart));
        double todayNewUsersDelta = percentDelta(todayNewUsers, yesterdayNewUsers);

        // ---- KPI: 今日 AI 会话量，对比昨日 ----
        long aiToday = aiConversationMapper.selectCount(
                new QueryWrapper<AiConversationEntity>().ge("created_at", todayStart));
        long aiYesterday = countAiBetween(todayStart.minusDays(1), todayStart);
        double aiDelta = percentDelta(aiToday, aiYesterday);

        // ---- KPI: 待受理申请（service_request SUBMITTED 计数；原 status='PENDING' 不存在于状态机，恒 0）----
        long pendingLeaves = serviceRequestMapper.selectCount(reqWrapper().eq("status", "SUBMITTED"));
        long pendingLeavesDelta = 0; // 增量口径未定义，待接历史快照后计算；前端当前未消费该字段

        // ---- 近 7 天工单趋势（按天统计创建/完成）----
        List<DashboardResponse.TrendPoint> trend = new ArrayList<>();
        long weekCreated = 0;
        long weekResolved = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            long created = countCreatedBetween(start, end, w -> { });
            long resolved = workOrderMapper.selectCount(wrapper()
                    .ge("completed_at", start).lt("completed_at", end));
            String label = i == 0 ? "今天" : day.getDayOfWeek().getValue() <= 5 ? "周" + "一二三四五六日".charAt(day.getDayOfWeek().getValue() - 1) : "";
            trend.add(new DashboardResponse.TrendPoint(label.isEmpty() ? day.format(DAY_LABEL) : label, created, resolved));
            weekCreated += created;
            weekResolved += resolved;
        }
        double resolveRate = weekCreated == 0 ? 0 : Math.round(weekResolved * 1000.0 / weekCreated) / 10.0;
        long dailyAvg = Math.round(weekCreated / 7.0);

        // ---- 分类占比（本周工单按关联服务类目聚合，无类目工单归"其他"）----
        Map<String, Long> byItem = new LinkedHashMap<>();
        List<WorkOrderEntity> weekOrders = workOrderMapper.selectList(wrapper()
                .ge("created_at", weekStart));
        for (WorkOrderEntity o : weekOrders) {
            String key = o.getItemId() == null ? "其他" : "服务 #" + o.getItemId();
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
                pendingLeaves, pendingLeavesDelta,
                weekCreated, weekResolved, resolveRate, dailyAvg,
                trend, categories, latestOrders);
    }

    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<WorkOrderEntity> wrapper() {
        return new QueryWrapper<WorkOrderEntity>().eq("deleted", 0);
    }

    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ServiceRequestEntity> reqWrapper() {
        return new QueryWrapper<ServiceRequestEntity>().eq("deleted", 0);
    }

    private long countCreatedBetween(LocalDateTime start, LocalDateTime end,
                                     java.util.function.Consumer<QueryWrapper<WorkOrderEntity>> extra) {
        QueryWrapper<WorkOrderEntity> w = wrapper().ge("created_at", start).lt("created_at", end);
        extra.accept(w);
        return workOrderMapper.selectCount(w);
    }

    private long countAiBetween(LocalDateTime start, LocalDateTime end) {
        return aiConversationMapper.selectCount(
                new QueryWrapper<AiConversationEntity>().ge("created_at", start).lt("created_at", end));
    }

    private double percentDelta(long now, long before) {
        if (before == 0) {
            return now == 0 ? 0 : 100.0;
        }
        return Math.round((now - before) * 1000.0 / before) / 10.0;
    }
}
