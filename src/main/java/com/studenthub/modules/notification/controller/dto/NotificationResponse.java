package com.studenthub.modules.notification.controller.dto;

import com.studenthub.modules.notification.entity.NotificationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "站内通知响应项")
public record NotificationResponse(
        @Schema(description = "通知 ID") Long notificationId,
        @Schema(description = "通知类型") String notificationType,
        @Schema(description = "标题") String title,
        @Schema(description = "内容") String content,
        @Schema(description = "关联业务类型", nullable = true) String relatedType,
        @Schema(description = "关联业务 ID", nullable = true) Long relatedId,
        @Schema(description = "是否已读") boolean read,
        @Schema(description = "已读时间", nullable = true) LocalDateTime readAt,
        @Schema(description = "发送时间") LocalDateTime createdAt) {

    public static NotificationResponse from(NotificationEntity entity) {
        return new NotificationResponse(entity.getId(), entity.getNotificationType(), entity.getTitle(),
                entity.getContent(), entity.getRelatedType(), entity.getRelatedId(),
                entity.getReadAt() != null, entity.getReadAt(), entity.getCreatedAt());
    }
}
