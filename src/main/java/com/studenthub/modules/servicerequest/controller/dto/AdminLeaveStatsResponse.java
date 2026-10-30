package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端请假统计")
public record AdminLeaveStatsResponse(
        @Schema(description = "待审批") long pending,
        @Schema(description = "已通过（生效中）") long approved,
        @Schema(description = "已驳回") long rejected,
        @Schema(description = "已销假") long cancelled,
        @Schema(description = "合计") long total) {
}
