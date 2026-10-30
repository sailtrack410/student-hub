package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 投放计划新建/更新请求（C13）。 */
@Schema(description = "投放计划请求")
public record AdminAdPlanRequest(
        @Schema(description = "计划名称") @NotBlank @Size(max = 100) String planName,
        @Schema(description = "关联广告主 ID（可空）") Long advertiserId,
        @Schema(description = "投放目标：AWARENESS/TRAFFIC/CONVERSION") @Size(max = 32) String objective,
        @Schema(description = "广告位：FEED/BANNER/SPLASH/SERVICE_HALL") @Size(max = 16) String slot,
        @Schema(description = "开始时间") @NotNull LocalDateTime startAt,
        @Schema(description = "结束时间") @NotNull LocalDateTime endAt,
        @Schema(description = "总预算（分，可空）") Long budgetCents,
        @Schema(description = "日预算（分，可空）") Long dailyBudgetCents,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
