package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "模型更新请求")
public record AiModelUpdateRequest(
        @Schema(description = "是否启用", nullable = true) Boolean enabled,
        @Schema(description = "模型类型：CHAT/VISION", nullable = true)
        @Pattern(regexp = "^(CHAT|VISION)$", message = "模型类型仅支持 CHAT/VISION") String modelType) {
}
