package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "设置类型默认模型请求（modelId 为空表示清除该类型默认）")
public record AiDefaultModelSetRequest(
        @Schema(description = "模型 ID，为空清除默认", nullable = true) Long modelId,
        @Schema(description = "模型类型：CHAT/VISION/ASR")
        @Pattern(regexp = "^(CHAT|VISION|ASR|EMBEDDING)$", message = "模型类型仅支持 CHAT/VISION/ASR/EMBEDDING") String typeCode) {
}
