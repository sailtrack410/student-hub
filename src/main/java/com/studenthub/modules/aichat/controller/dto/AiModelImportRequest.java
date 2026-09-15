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
            @Schema(description = "模型类型：CHAT/VISION/ASR，默认 CHAT", nullable = true)
            @Pattern(regexp = "^(CHAT|VISION|ASR|EMBEDDING)$", message = "模型类型仅支持 CHAT/VISION/ASR/EMBEDDING") String modelType) {
    }
}
