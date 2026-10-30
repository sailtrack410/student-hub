package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "广告曝光上报结果")
public record AdImpressionResponse(
        @Schema(description = "事件 ID") Long eventId,
        @Schema(description = "点击链路 ID") String clickId) {
}
