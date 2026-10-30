package com.studenthub.modules.push.content.controller.dto;

import com.studenthub.modules.push.content.entity.CreatorProfileEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "创作者档案")
public record CreatorProfileResponse(
        Long id,
        Long campusId,
        Long userId,
        String username,
        String phoneMasked,
        String displayName,
        String certificationStatus,
        String certificationNote,
        Integer level,
        Integer score,
        String status,
        LocalDateTime mutedUntil,
        String bannedReason,
        LocalDateTime lastActiveAt,
        String remark,
        @Schema(description = "当前生效的处罚数量") long activePenaltyCount,
        @Schema(description = "处罚记录（详情接口返回，列表为 null）") List<CreatorPenaltyResponse> penalties,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CreatorProfileResponse from(CreatorProfileEntity entity, long activePenaltyCount) {
        return from(entity, activePenaltyCount, null);
    }

    public static CreatorProfileResponse from(CreatorProfileEntity entity, long activePenaltyCount,
                                              List<CreatorPenaltyResponse> penalties) {
        return new CreatorProfileResponse(
                entity.getId(), entity.getCampusId(), entity.getUserId(), null, null,
                entity.getDisplayName(), entity.getCertificationStatus(), entity.getCertificationNote(),
                entity.getLevel(), entity.getScore(), entity.getStatus(), entity.getMutedUntil(),
                entity.getBannedReason(), entity.getLastActiveAt(), entity.getRemark(),
                activePenaltyCount, penalties, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public CreatorProfileResponse withUser(String username, String phoneMasked) {
        return new CreatorProfileResponse(
                id, campusId, userId, username, phoneMasked, displayName, certificationStatus,
                certificationNote, level, score, status, mutedUntil, bannedReason, lastActiveAt,
                remark, activePenaltyCount, penalties, createdAt, updatedAt);
    }
}
