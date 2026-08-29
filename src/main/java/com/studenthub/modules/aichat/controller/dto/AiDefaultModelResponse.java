package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "类型默认模型")
public record AiDefaultModelResponse(
        @Schema(description = "模型类型：CHAT 聊天模型 / VISION 图片识别") String typeCode,
        @Schema(description = "类型显示名") String typeName,
        @Schema(description = "默认模型，未设置为 null", nullable = true) AiModelResponse model) {
}
