package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "创建 AI 会话请求")
public record CreateConversationRequest(
        @Schema(description = "会话标题，不填默认为「新对话」", nullable = true) @Size(max = 128) String title) {
}
