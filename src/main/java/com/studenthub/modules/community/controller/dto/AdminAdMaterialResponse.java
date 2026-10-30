package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.community.entity.CommunityAdMaterialEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 广告素材响应（C13）。 */
@Schema(description = "广告素材")
public record AdminAdMaterialResponse(
        Long materialId,
        Long campusId,
        String materialName,
        String materialType,
        String fileUrl,
        String coverUrl,
        String title,
        String description,
        Integer width,
        Integer height,
        Long sizeBytes,
        String reviewStatus,
        String reviewReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String status,
        long linkedAdCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminAdMaterialResponse from(CommunityAdMaterialEntity entity, long linkedAdCount) {
        return new AdminAdMaterialResponse(entity.getId(), entity.getCampusId(), entity.getMaterialName(),
                entity.getMaterialType(), entity.getFileUrl(), entity.getCoverUrl(), entity.getTitle(),
                entity.getDescription(), entity.getWidth(), entity.getHeight(), entity.getSizeBytes(),
                entity.getReviewStatus(), entity.getReviewReason(), entity.getReviewedBy(), entity.getReviewedAt(),
                entity.getStatus(), linkedAdCount, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
