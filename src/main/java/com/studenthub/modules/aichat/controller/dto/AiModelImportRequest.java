package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "批量导入模型请求")
public record AiModelImportRequest(
        @Schema(description = "待导入模型列表")
        @NotEmpty @Valid List<AiModelImportItem> models) {

    @Schema(description = "导入模型项")
    public record AiModelImportItem(
            @Schema(description = "供应商模型 ID") @NotBlank @Size(max = 128) String providerModelId,
            @Schema(description = "主类型：CHAT/VISION/ASR/TTS/EMBEDDING/RERANK；留空按模型 ID 自动推断", nullable = true)
            @Pattern(regexp = "^(CHAT|VISION|ASR|TTS|EMBEDDING|RERANK)$", message = "模型类型仅支持 CHAT/VISION/ASR/TTS/EMBEDDING/RERANK") String modelType,
            @Schema(description = "能力标签列表（多选，如 vision+stream），留空按模型 ID 自动推断", nullable = true)
            List<@Pattern(regexp = "^(stream|vision|audio|tools|structured|reasoning)$",
                    message = "能力仅支持 stream/vision/audio/tools/structured/reasoning") String> capabilities) {

        /** 归一化能力标签：去重、排序、逗号连接 */
        public String capabilitiesJoined() {
            if (capabilities == null || capabilities.isEmpty()) return null;
            return capabilities.stream().distinct().sorted().reduce((a, b) -> a + "," + b).orElse(null);
        }
    }
}
