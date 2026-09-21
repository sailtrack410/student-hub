package com.studenthub.modules.dashboard.controller.dto;

import java.util.List;

/**
 * 管理端工作台聚合数据。
 * KPI 四项 + 近 7 天工单趋势 + 分类占比 + 最新工单。
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
        List<TrendPoint> trend,
        List<CategorySlice> categories,
        List<LatestOrder> latestOrders
) {

    /** 近 7 天单日双柱：新增 / 已解决 */
    public record TrendPoint(String day, long created, long resolved) {
    }

    /** 工单分类占比 */
    public record CategorySlice(String name, double percent) {
    }

    /** 最新工单 */
    public record LatestOrder(Long orderId, String orderNo, String title, String status, String createdAt) {
    }
}
