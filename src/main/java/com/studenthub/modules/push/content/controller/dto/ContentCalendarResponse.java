package com.studenthub.modules.push.content.controller.dto;

import com.studenthub.modules.push.content.entity.ContentCalendarEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端内容日历排期")
public record ContentCalendarResponse(
        Long id,
        Long campusId,
        Long slotId,
        @Schema(description = "绑定运营位名称（未绑定时为 null）") String slotName,
        String entryType,
        String title,
        String summary,
        String coverUrl,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        @Schema(description = "派生运行态：ONGOING/NOT_STARTED/EXPIRED/PLANNED/OFFLINE") String runtimeState,
        String owner,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ContentCalendarResponse from(ContentCalendarEntity entity, String slotName) {
        return new ContentCalendarResponse(
                entity.getId(), entity.getCampusId(), entity.getSlotId(), slotName,
                entity.getEntryType(), entity.getTitle(), entity.getSummary(), entity.getCoverUrl(),
                entity.getStartAt(), entity.getEndAt(), entity.getStatus(), runtimeState(entity),
                entity.getOwner(), entity.getRemark(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /** 仅 PUBLISHED 的排期参与运行态判断；PLANNED/OFFLINE 直接按状态展示。 */
    public static String runtimeState(ContentCalendarEntity entity) {
        if ("PLANNED".equals(entity.getStatus())) {
            return "PLANNED";
        }
        if (!"PUBLISHED".equals(entity.getStatus())) {
            return "OFFLINE";
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getStartAt() != null && now.isBefore(entity.getStartAt())) {
            return "NOT_STARTED";
        }
        if (entity.getEndAt() != null && now.isAfter(entity.getEndAt())) {
            return "EXPIRED";
        }
        return "ONGOING";
    }
}
