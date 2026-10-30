package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "信息流广告卡片")
public record FeedAdItemResponse(
        @Schema(description = "广告 ID") Long adId,
        @Schema(description = "本次展示对应的点击链路 ID，上报曝光/点击时回传") String clickId,
        @Schema(description = "广告标题") String title,
        @Schema(description = "广告主品牌名") String brandName,
        @Schema(description = "广告描述") String description,
        @Schema(description = "封面图地址") String coverUrl,
        @Schema(description = "落地地址/路由") String targetUrl,
        @Schema(description = "跳转类型") String landingType,
        @Schema(description = "定向分区 ID") Long sectionId,
        @Schema(description = "展示优先级") Integer priority,
        @Schema(description = "是否可测跳出（IN_APP_WEBVIEW/APP_PAGE）") boolean measurable,
        @Schema(description = "推广标识文案") String sponsoredLabel,
        @Schema(description = "CTA 按钮文案") String ctaText) {
}
