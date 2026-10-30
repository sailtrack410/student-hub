package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "工单 SLA 统计")
public record WorkOrderSlaStatsResponse(
        @Schema(description = "待派发工单数") long pendingDispatch,
        @Schema(description = "已派发待开始工单数") long assigned,
        @Schema(description = "处理中工单数") long processing,
        @Schema(description = "现场照片已记录工单数") long onsitePhotoRecorded,
        @Schema(description = "待确认工单数") long pendingConfirmation,
        @Schema(description = "进行中工单总数") long inProgressTotal,
        @Schema(description = "已超时工单数") long overdue,
        @Schema(description = "即将超时工单数（4 小时内）") long dueSoon,
        @Schema(description = "近 30 天完成工单数") long completedLast30Days,
        @Schema(description = "平均处理时长（小时，派单→完成）", nullable = true) Double avgProcessHours,
        @Schema(description = "平均办结时长（小时，创建→完成）", nullable = true) Double avgCompletionHours,
        @Schema(description = "进行中工单超时率（百分比，0-100）", nullable = true) Double overdueRate) {
}
