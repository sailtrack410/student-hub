package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "管理端官方直投广告创建/编辑")
public record AdminDirectAdRequest(
        @Schema(description = "广告标题") @NotBlank @Size(max = 100) String title,
        @Schema(description = "广告主品牌名") @NotBlank @Size(max = 100) String brandName,
        @Schema(description = "关联广告主 ID（需与广告同校区）") Long advertiserId,
        @Schema(description = "广告描述") @Size(max = 500) String description,
        @Schema(description = "封面图地址") @Size(max = 512) String coverUrl,
        @Schema(description = "落地地址/路由") @NotBlank @Size(max = 512) String targetUrl,
        @Schema(description = "跳转类型：IN_APP_WEBVIEW/APP_PAGE/EXTERNAL_BROWSER/DEEPLINK")
        @NotBlank @Size(max = 32) String landingType,
        @Schema(description = "定向分区 ID，空表示不限分区") Long sectionId,
        @Schema(description = "广告位：FEED/BANNER/SPLASH/SERVICE_HALL，缺省 FEED") @Size(max = 16) String slot,
        @Schema(description = "展示优先级，越大越靠前") @Min(-1000) @Max(1000) Integer priority,
        @Schema(description = "投放开始时间") @NotNull LocalDateTime startAt,
        @Schema(description = "投放结束时间") @NotNull LocalDateTime endAt,
        @Schema(description = "配置状态：DRAFT/ENABLED/DISABLED，缺省 DRAFT") @Size(max = 16) String status,
        @Schema(description = "跳出统计窗口 N（秒），缺省 5") @Min(1) @Max(60) Integer bounceThresholdSeconds,
        @Schema(description = "CTA 按钮文案") @Size(max = 32) String ctaText,
        @Schema(description = "推广标识文案，缺省「推广」") @Size(max = 32) String sponsoredLabel,
        @Schema(description = "关联投放计划 ID（可空，需与广告同校区）", nullable = true) Long planId,
        @Schema(description = "关联广告素材 ID（可空，素材必须审核通过且同校区）", nullable = true) Long materialId) {
}
