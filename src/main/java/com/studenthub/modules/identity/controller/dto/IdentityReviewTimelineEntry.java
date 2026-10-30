package com.studenthub.modules.identity.controller.dto;

import com.studenthub.modules.identity.entity.UserIdentityReviewEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "身份审核历史条目（真实记录，一次通过/拒绝一条）")
public record IdentityReviewTimelineEntry(
        Long id,
        String action,
        String fromStatus,
        String toStatus,
        String reasonCode,
        String reason,
        String evidenceSnapshot,
        Long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt) {

    public static IdentityReviewTimelineEntry from(UserIdentityReviewEntity entity) {
        return new IdentityReviewTimelineEntry(
                entity.getId(), entity.getAction(), entity.getFromStatus(), entity.getToStatus(),
                entity.getReasonCode(), entity.getReason(), entity.getEvidenceSnapshot(),
                entity.getReviewerId(), entity.getReviewerName(), entity.getReviewedAt());
    }
}
