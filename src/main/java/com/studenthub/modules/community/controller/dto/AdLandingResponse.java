package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "广告落地上报结果")
public record AdLandingResponse(
        @Schema(description = "事件 ID") Long eventId,
        @Schema(description = "点击链路 ID") String clickId,
        @Schema(description = "是否可测跳出") boolean measurable) {
}
