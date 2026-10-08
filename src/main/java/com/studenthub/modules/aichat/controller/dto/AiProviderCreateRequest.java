package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "新增 AI 供应商请求")
public record AiProviderCreateRequest(
        @Schema(description = "显示名称") @NotBlank @Size(max = 64) String providerName,
        @Schema(description = "服务地址，如 https://api.siliconflow.cn/v1") @NotBlank @Size(max = 255) String baseUrl,
        @Schema(description = "API Key") @NotBlank @Size(max = 512) String apiKey,
        @Schema(description = "供应商编码，留空则自动生成", nullable = true) @Size(max = 64) String providerCode,
        @Schema(description = "供应商图标标识（lobe-icons 名，如 siliconflow / openai / anthropic）", nullable = true)
        @Size(max = 64) String icon,
        @Schema(description = "调用协议：OPENAI_CHAT（/chat/completions）/ ANTHROPIC_MESSAGES（/messages）/ OPENAI_RESPONSES（/responses），默认 OPENAI_CHAT",
                nullable = true) @Size(max = 32)
        @Pattern(regexp = "OPENAI_CHAT|ANTHROPIC_MESSAGES|OPENAI_RESPONSES", message = "协议仅支持 OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES") String protocol,
        @Schema(description = "支持流式输出（SSE）", nullable = true) Boolean streaming) {
}
