package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiConversationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "AI 会话响应")
public record AiConversationResponse(
        @Schema(description = "会话 ID") Long conversationId,
        @Schema(description = "会话标题") String title,
        @Schema(description = "创建时间") LocalDateTime createdAt) {

    public static AiConversationResponse from(AiConversationEntity entity) {
        return new AiConversationResponse(entity.getId(), entity.getTitle(), entity.getCreatedAt());
    }
}
