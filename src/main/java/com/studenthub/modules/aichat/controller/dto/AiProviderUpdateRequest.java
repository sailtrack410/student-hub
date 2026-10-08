package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "编辑 AI 供应商请求（API Key 留空表示保持原值）")
public record AiProviderUpdateRequest(
        @Schema(description = "显示名称", nullable = true) @Size(max = 64) String providerName,
        @Schema(description = "服务地址", nullable = true) @Size(max = 255) String baseUrl,
        @Schema(description = "API Key，留空保持原值", nullable = true) @Size(max = 512) String apiKey,
        @Schema(description = "是否启用", nullable = true) Boolean enabled,
        @Schema(description = "供应商图标标识（lobe-icons 名）", nullable = true) @Size(max = 64) String icon,
        @Schema(description = "调用协议：OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES", nullable = true) @Size(max = 32)
        @Pattern(regexp = "OPENAI_CHAT|ANTHROPIC_MESSAGES|OPENAI_RESPONSES", message = "协议仅支持 OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES") String protocol,
        @Schema(description = "支持流式输出（SSE）", nullable = true) Boolean streaming) {
}
