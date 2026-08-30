package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "公开 AI 问答响应")
public record PublicChatResponse(String answer, String model) {
}