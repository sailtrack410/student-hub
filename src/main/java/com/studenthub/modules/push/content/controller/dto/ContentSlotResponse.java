package com.studenthub.modules.push.content.controller.dto;

import com.studenthub.modules.push.content.entity.ContentSlotEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端内容运营位")
public record ContentSlotResponse(
        Long id,
        Long campusId,
        String slotCode,
        String slotName,
        String slotType,
        String position,
        String title,
        String subtitle,
        String imageUrl,
        String targetType,
        String targetValue,
        Integer weight,
        Integer sortOrder,
        String status,
        @Schema(description = "派生运行态：ONLINE/NOT_STARTED/EXPIRED/OFFLINE/DRAFT") String runtimeState,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ContentSlotResponse from(ContentSlotEntity entity) {
        return new ContentSlotResponse(
                entity.getId(), entity.getCampusId(), entity.getSlotCode(), entity.getSlotName(),
                entity.getSlotType(), entity.getPosition(), entity.getTitle(), entity.getSubtitle(),
                entity.getImageUrl(), entity.getTargetType(), entity.getTargetValue(),
                entity.getWeight(), entity.getSortOrder(), entity.getStatus(),
                runtimeState(entity), entity.getStartAt(), entity.getEndAt(), entity.getRemark(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /** 运行态由状态与时间窗口共同决定，避免前端各自推断。 */
    public static String runtimeState(ContentSlotEntity entity) {
        if (!"ONLINE".equals(entity.getStatus())) {
            return "DRAFT".equals(entity.getStatus()) ? "DRAFT" : "OFFLINE";
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getStartAt() != null && now.isBefore(entity.getStartAt())) {
            return "NOT_STARTED";
        }
        if (entity.getEndAt() != null && now.isAfter(entity.getEndAt())) {
            return "EXPIRED";
        }
        return "ONLINE";
    }
}
