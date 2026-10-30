package com.studenthub.modules.push.controller.dto;

import com.studenthub.modules.push.entity.PushTaskDeliveryEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "推送逐用户投递明细")
public record PushDeliveryResponse(
        Long id,
        Long taskId,
        Long userId,
        String channel,
        String status,
        String providerMessage,
        String errorCode,
        String errorMessage,
        Integer attempts,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime clickedAt) {

    public static PushDeliveryResponse from(PushTaskDeliveryEntity entity) {
        return new PushDeliveryResponse(entity.getId(), entity.getTaskId(), entity.getUserId(),
                entity.getChannel(), entity.getStatus(), entity.getProviderMessage(),
                entity.getErrorCode(), entity.getErrorMessage(), entity.getAttempts(),
                entity.getSentAt(), entity.getDeliveredAt(), entity.getClickedAt());
    }
}
