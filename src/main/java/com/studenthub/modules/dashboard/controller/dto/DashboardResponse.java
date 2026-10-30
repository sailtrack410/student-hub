package com.studenthub.modules.dashboard.controller.dto;

import java.util.List;

/**
 * 管理端工作台聚合数据。
 * KPI + 今日待办 + 快捷入口 + 日期范围内的工单趋势/分类占比/最新工单。
 */
public record DashboardResponse(
        long pendingWorkOrders,
        double pendingDeltaPercent,
        long todayNewUsers,
        double todayNewUsersDeltaPercent,
        long todayAiSessions,
        double todayAiSessionsDeltaPercent,
        long pendingRequests,
        long pendingRequestsDelta,
        long weekNewWorkOrders,
        long weekResolvedWorkOrders,
        double resolveRatePercent,
        long dailyAvgNew,
        String rangeStart,
        String rangeEnd,
        long todoTotal,
        List<TodoItem> todos,
        List<QuickEntry> quickEntries,
        List<TrendPoint> trend,
        List<CategorySlice> categories,
        List<LatestOrder> latestOrders
) {

    /** 今日待办队列：count 为 0 时前端可弱化展示。 */
    public record TodoItem(String key, String label, long count, String route, String permission, String tone) {
    }

    /** 快捷入口元数据，前端按当前账号权限过滤。 */
    public record QuickEntry(String key, String label, String route, String permission) {
    }

    /** 日期范围内单日双柱：新增 / 已解决 */
    public record TrendPoint(String day, long created, long resolved) {
    }

    /** 工单分类占比 */
    public record CategorySlice(String name, double percent) {
    }

    /** 最新工单 */
    public record LatestOrder(Long orderId, String orderNo, String title, String status, String createdAt) {
    }
}
