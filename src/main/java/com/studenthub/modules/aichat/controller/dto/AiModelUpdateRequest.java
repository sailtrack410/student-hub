package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.enums.AiModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "模型更新请求")
public record AiModelUpdateRequest(
        @Schema(description = "是否启用", nullable = true) Boolean enabled,
        @Schema(description = "模型类型：CHAT/VISION/ASR/TTS/EMBEDDING/RERANK", nullable = true)
        @Pattern(regexp = AiModelType.CODE_PATTERN, message = "模型类型仅支持 CHAT/VISION/ASR/TTS/EMBEDDING/RERANK") String modelType,
        @Schema(description = "CHAT 模型上下文窗口（tokens），null 保持原值", nullable = true) Integer contextWindowTokens,
        @Schema(description = "CHAT 模型最大输出（tokens），null 保持原值", nullable = true) Integer maxOutputTokens) {
}
