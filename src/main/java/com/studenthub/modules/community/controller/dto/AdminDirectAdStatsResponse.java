package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "官方直投广告数据统计")
public record AdminDirectAdStatsResponse(
        @Schema(description = "广告 ID") Long adId,
        @Schema(description = "曝光数") long impressions,
        @Schema(description = "点击数（含不可测落地类型）") long clicks,
        @Schema(description = "点击率 = 点击/曝光，无曝光时为 0") double ctr,
        @Schema(description = "可测落地数：收到 landing/loaded 的点击（去重 clickId）") long measurableLandings,
        @Schema(description = "N 秒内跳出数") long bounced,
        @Schema(description = "跳出率 = 跳出数/可测落地数；无可测落地时为 null（不可测）") Double bounceRate,
        @Schema(description = "平均停留毫秒 = sum(dwell_ms)/可测落地数；无可测落地时为 null") Double avgDwellMs,
        @Schema(description = "不可测点击（外部浏览器/DEEPLINK）") long unmeasurableClicks,
        @Schema(description = "是否存在可测落地，false 时跳出率不可测") boolean bounceMeasurable,
        @Schema(description = "本次统计使用的跳出窗口 N（秒）") int bounceThresholdSeconds,
        @Schema(description = "按落地类型拆分") List<LandingTypeStats> landingBreakdown) {

    @Schema(description = "按落地类型拆分的统计")
    public record LandingTypeStats(
            @Schema(description = "落地类型") String landingType,
            @Schema(description = "点击数") long clicks,
            @Schema(description = "是否可测跳出") boolean measurable,
            @Schema(description = "可测落地数") long measurableLandings,
            @Schema(description = "跳出率；不可测或无落地时为 null") Double bounceRate,
            @Schema(description = "平均停留毫秒；不可测或无落地时为 null") Double avgDwellMs) {
    }
}
