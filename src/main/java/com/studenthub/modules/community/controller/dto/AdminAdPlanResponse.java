package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.community.entity.CommunityAdPlanEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 投放计划响应（C13）。 */
@Schema(description = "投放计划")
public record AdminAdPlanResponse(
        Long planId,
        Long campusId,
        Long advertiserId,
        String advertiserName,
        String planName,
        String objective,
        String slot,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long budgetCents,
        Long dailyBudgetCents,
        String status,
        String effectiveStatus,
        String remark,
        long adCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static String effectiveStatus(CommunityAdPlanEntity entity, LocalDateTime now) {
        if ("ENABLED".equals(entity.getStatus()) && entity.getEndAt() != null && !entity.getEndAt().isAfter(now)) {
            return "EXPIRED";
        }
        return entity.getStatus();
    }

    public static AdminAdPlanResponse from(CommunityAdPlanEntity entity, String advertiserName,
                                           long adCount, LocalDateTime now) {
        return new AdminAdPlanResponse(entity.getId(), entity.getCampusId(), entity.getAdvertiserId(),
                advertiserName, entity.getPlanName(), entity.getObjective(), entity.getSlot(),
                entity.getStartAt(), entity.getEndAt(), entity.getBudgetCents(), entity.getDailyBudgetCents(),
                entity.getStatus(), effectiveStatus(entity, now), entity.getRemark(), adCount,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
