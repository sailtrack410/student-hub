package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "广告点击上报结果")
public record AdClickResponse(
        @Schema(description = "点击链路 ID，后续 landing/loaded 与 landing/exit 必须回传") String clickId,
        @Schema(description = "广告 ID") Long adId,
        @Schema(description = "落地地址/路由") String targetUrl,
        @Schema(description = "跳转类型") String landingType,
        @Schema(description = "是否可测跳出；EXTERNAL_BROWSER/DEEPLINK 为 false") boolean measurable) {
}
