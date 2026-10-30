package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "广告停留/跳出上报结果")
public record AdLandingExitResponse(
        @Schema(description = "点击链路 ID") String clickId,
        @Schema(description = "停留毫秒") Long dwellMs,
        @Schema(description = "是否在 N 秒内跳出") boolean bounced,
        @Schema(description = "广告配置的跳出统计窗口 N（秒）") int bounceThresholdSeconds) {
}
