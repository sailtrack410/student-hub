package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.community.entity.CommunityDirectAdEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端官方直投广告")
public record AdminDirectAdResponse(
        @Schema(description = "广告 ID") Long adId,
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "关联广告主 ID") Long advertiserId,
        @Schema(description = "关联广告主名称") String advertiserName,
        @Schema(description = "广告标题") String title,
        @Schema(description = "广告主品牌名") String brandName,
        @Schema(description = "广告描述") String description,
        @Schema(description = "封面图地址") String coverUrl,
        @Schema(description = "落地地址/路由") String targetUrl,
        @Schema(description = "跳转类型") String landingType,
        @Schema(description = "定向分区 ID") Long sectionId,
        @Schema(description = "广告位") String slot,
        @Schema(description = "展示优先级") Integer priority,
        @Schema(description = "投放开始时间") LocalDateTime startAt,
        @Schema(description = "投放结束时间") LocalDateTime endAt,
        @Schema(description = "配置状态") String status,
        @Schema(description = "对外展示状态：DRAFT/ENABLED/DISABLED/EXPIRED（已过结束时间）") String effectiveStatus,
        @Schema(description = "审核状态：DRAFT/PENDING/APPROVED/REJECTED") String reviewStatus,
        @Schema(description = "驳回原因") String reviewReason,
        @Schema(description = "最近一次提交审核时间") LocalDateTime submittedAt,
        @Schema(description = "审核时间") LocalDateTime reviewedAt,
        @Schema(description = "是否可测跳出：IN_APP_WEBVIEW/APP_PAGE 为 true") boolean bounceMeasurable,
        @Schema(description = "跳出统计窗口 N（秒）") Integer bounceThresholdSeconds,
        @Schema(description = "CTA 按钮文案") String ctaText,
        @Schema(description = "推广标识文案") String sponsoredLabel,
        @Schema(description = "关联投放计划 ID") Long planId,
        @Schema(description = "关联投放计划名称") String planName,
        @Schema(description = "关联素材 ID") Long materialId,
        @Schema(description = "关联素材名称") String materialName,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    /** 已过结束时间的 ENABLED 广告对外展示为 EXPIRED，但不改库，便于运营补时或下线。 */
    public static String effectiveStatus(CommunityDirectAdEntity entity, LocalDateTime now) {
        if ("ENABLED".equals(entity.getStatus()) && !entity.getEndAt().isAfter(now)) {
            return "EXPIRED";
        }
        return entity.getStatus();
    }

    public static AdminDirectAdResponse from(CommunityDirectAdEntity entity, LocalDateTime now) {
        return from(entity, now, null);
    }

    public static AdminDirectAdResponse from(CommunityDirectAdEntity entity, LocalDateTime now,
                                             String advertiserName) {
        return from(entity, now, advertiserName, null, null);
    }

    public static AdminDirectAdResponse from(CommunityDirectAdEntity entity, LocalDateTime now,
                                             String advertiserName, String planName,
                                             String materialName) {
        boolean measurable = "IN_APP_WEBVIEW".equals(entity.getLandingType())
                || "APP_PAGE".equals(entity.getLandingType());
        return new AdminDirectAdResponse(entity.getId(), entity.getCampusId(), entity.getAdvertiserId(),
                advertiserName, entity.getTitle(), entity.getBrandName(), entity.getDescription(),
                entity.getCoverUrl(), entity.getTargetUrl(), entity.getLandingType(), entity.getSectionId(),
                entity.getSlot(), entity.getPriority(), entity.getStartAt(), entity.getEndAt(), entity.getStatus(),
                effectiveStatus(entity, now), entity.getReviewStatus(), entity.getReviewReason(),
                entity.getSubmittedAt(), entity.getReviewedAt(), measurable,
                entity.getBounceThresholdSeconds(), entity.getCtaText(), entity.getSponsoredLabel(),
                entity.getPlanId(), planName, entity.getMaterialId(), materialName,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
