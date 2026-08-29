package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "编辑 AI 供应商请求（API Key 留空表示保持原值）")
public record AiProviderUpdateRequest(
        @Schema(description = "显示名称", nullable = true) @Size(max = 64) String providerName,
        @Schema(description = "服务地址", nullable = true) @Size(max = 255) String baseUrl,
        @Schema(description = "API Key，留空保持原值", nullable = true) @Size(max = 512) String apiKey,
        @Schema(description = "是否启用", nullable = true) Boolean enabled) {
}
