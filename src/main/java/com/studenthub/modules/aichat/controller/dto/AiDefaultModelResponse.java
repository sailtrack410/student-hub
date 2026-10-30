package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "类型默认模型")
public record AiDefaultModelResponse(
        @Schema(description = "模型类型：CHAT 聊天模型 / VISION 图片识别 / ASR 语音识别") String typeCode,
        @Schema(description = "类型显示名") String typeName,
        @Schema(description = "默认模型，未设置为 null", nullable = true) AiModelResponse model,
        @Schema(description = "是否被知识库向量锁定（仅 EMBEDDING）") boolean locked,
        @Schema(description = "锁定说明/不可更改原因") String lockMessage) {
}
