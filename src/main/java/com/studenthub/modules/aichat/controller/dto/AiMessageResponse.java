package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiMessageEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "AI 消息响应")
public record AiMessageResponse(
        @Schema(description = "消息 ID") Long messageId,
        @Schema(description = "角色 USER/ASSISTANT") String role,
        @Schema(description = "消息内容") String content,
        @Schema(description = "是否降级回复") boolean degraded,
        @Schema(description = "发送时间") LocalDateTime createdAt) {

    public static AiMessageResponse from(AiMessageEntity entity) {
        return new AiMessageResponse(entity.getId(), entity.getRole(), entity.getContent(),
                "FAILED".equals(entity.getStatus()), entity.getCreatedAt());
    }
}
