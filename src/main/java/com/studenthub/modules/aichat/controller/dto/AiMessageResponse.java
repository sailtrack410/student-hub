package com.studenthub.modules.aichat.controller.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 消息响应")
public record AiMessageResponse(
        @Schema(description = "消息 ID") Long messageId,
        @Schema(description = "角色 USER/ASSISTANT") String role,
        @Schema(description = "消息内容") String content,
        @Schema(description = "是否降级回复") boolean degraded,
        @Schema(description = "动作卡片，可空") List<AiActionResponse> actions,
        @Schema(description = "发送时间") LocalDateTime createdAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AiMessageResponse from(AiMessageEntity entity) {
        return new AiMessageResponse(entity.getId(), entity.getRole(), entity.getContent(),
                "FAILED".equals(entity.getStatus()), parseActions(entity.getActions()), entity.getCreatedAt());
    }

    private static List<AiActionResponse> parseActions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<AiActionResponse>>() { });
        } catch (Exception ignored) {
            return null;
        }
    }
}