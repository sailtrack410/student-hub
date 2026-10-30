package com.studenthub.modules.dashboard.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 数据分析总览：服务申请 / 工单 / 社区 / 用户 / AI 五类指标、按天趋势与分类占比。
 * 全部数字来自业务表按校区 + 时间窗聚合，不填充模拟数据。
 */
@Schema(description = "管理端数据分析总览")
public record AnalyticsOverviewResponse(
        @Schema(description = "统计开始日期") String rangeStart,
        @Schema(description = "统计结束日期") String rangeEnd,
        @Schema(description = "天数") int days,
        @Schema(description = "汇总指标") Kpis kpis,
        @Schema(description = "按天趋势") List<TrendPoint> trend,
        @Schema(description = "服务申请状态分布") List<Slice> requestStatus,
        @Schema(description = "工单状态分布") List<Slice> workOrderStatus,
        @Schema(description = "服务项 Top") List<Slice> topServices,
        @Schema(description = "社区分区 Top") List<Slice> topSections,
        @Schema(description = "新增用户身份分布") List<Slice> userIdentities,
        @Schema(description = "社区举报状态分布") List<Slice> reportStatus) {

    public record Kpis(long requestsCreated, long requestsCompleted, long workOrdersCreated,
                       long workOrdersCompleted, long posts, long comments, long newUsers,
                       long activeUsers, long aiSessions, long aiMessages) {
    }

    /** 五条趋势共用日期轴。 */
    public record TrendPoint(String date, long requests, long workOrders, long posts,
                             long newUsers, long aiSessions) {
    }

    public record Slice(String name, long value, double percent) {
    }
}
