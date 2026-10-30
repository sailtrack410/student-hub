package com.studenthub.modules.dashboard.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * SLA 看板：按服务项 / 组织聚合承诺时长、超时率、平均办结、满意度与返工/驳回。
 *
 * <p>截止时间口径：service_item.sla_hours 为承诺工作小时，按工作日历（服务时段 + 节假日/调休）
 * 推算 dueAt；工单 due_at 由工单模块按承诺时限写入并用于超时预警，看板按同一口径做跨服务项对比。</p>
 */
@Schema(description = "管理端 SLA 看板")
public record SlaOverviewResponse(
        @Schema(description = "统计开始日期") String rangeStart,
        @Schema(description = "统计结束日期") String rangeEnd,
        @Schema(description = "截止时间口径说明") String dueAtBasis,
        @Schema(description = "工作日历口径") HolidayInfo holidays,
        @Schema(description = "汇总") Summary summary,
        @Schema(description = "按服务项") List<ServiceRow> byService,
        @Schema(description = "按组织") List<OrgRow> byOrganization,
        @Schema(description = "超时/慢单 Top") List<SlowOrder> slowOrders) {

    public record HolidayInfo(int holidayDays, int extraWorkdays,
                              int workdayStartHour, int workdayEndHour, boolean weekendWorkday) {
    }

    public record Summary(long requests, long completed, long withSla, long overdue, long cancelled,
                          double overdueRatePercent, Double avgResolveHours, Double avgSatisfaction,
                          long rework, double reworkRatePercent, long rejected) {
    }

    public record ServiceRow(Long itemId, String itemName, Integer slaHours,
                             long requests, long completed, long overdue, double overdueRatePercent,
                             Double avgResolveHours, Double avgSatisfaction, long rework) {
    }

    public record OrgRow(Long organizationId, String organizationName,
                         long requests, long completed, long overdue, double overdueRatePercent,
                         Double avgResolveHours) {
    }

    public record SlowOrder(Long requestId, String requestNo, String itemName, String organizationName,
                            String status, String submittedAt, String deadlineAt, Double resolveHours,
                            boolean overdue) {
    }
}
