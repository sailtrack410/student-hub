package com.studenthub.modules.push.content.controller.dto;

import com.studenthub.modules.push.content.entity.CreatorPenaltyEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "创作者处罚记录")
public record CreatorPenaltyResponse(
        Long id,
        Long creatorId,
        Long userId,
        String penaltyType,
        String reason,
        String evidenceUrl,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        String appealStatus,
        String appealReason,
        LocalDateTime appealAt,
        String appealResult,
        Long handledBy,
        LocalDateTime handledAt,
        LocalDateTime createdAt) {

    public static CreatorPenaltyResponse from(CreatorPenaltyEntity entity) {
        return new CreatorPenaltyResponse(
                entity.getId(), entity.getCreatorId(), entity.getUserId(), entity.getPenaltyType(),
                entity.getReason(), entity.getEvidenceUrl(), entity.getStartAt(), entity.getEndAt(),
                entity.getStatus(), entity.getAppealStatus(), entity.getAppealReason(),
                entity.getAppealAt(), entity.getAppealResult(), entity.getHandledBy(),
                entity.getHandledAt(), entity.getCreatedAt());
    }
}
