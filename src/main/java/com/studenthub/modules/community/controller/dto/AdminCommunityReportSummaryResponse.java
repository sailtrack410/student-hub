package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端社区治理概览：举报量与生效处罚数，用于治理页看板")
public record AdminCommunityReportSummaryResponse(
        @Schema(description = "待处理举报数") long pendingCount,
        @Schema(description = "已成立举报数") long acceptedCount,
        @Schema(description = "已驳回举报数") long rejectedCount,
        @Schema(description = "今日新增举报数") long todayCount,
        @Schema(description = "当前生效的处罚数") long activeSanctionCount) {
}
