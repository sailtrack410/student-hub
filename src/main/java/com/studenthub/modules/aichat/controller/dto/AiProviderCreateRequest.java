package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "新增 AI 供应商请求")
public record AiProviderCreateRequest(
        @Schema(description = "显示名称") @NotBlank @Size(max = 64) String providerName,
        @Schema(description = "服务地址，如 https://api.siliconflow.cn/v1") @NotBlank @Size(max = 255) String baseUrl,
        @Schema(description = "API Key") @NotBlank @Size(max = 512) String apiKey,
        @Schema(description = "供应商编码，留空则自动生成", nullable = true) @Size(max = 64) String providerCode) {
}
