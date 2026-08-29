package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiModelEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端 AI 模型")
public record AiModelResponse(
        @Schema(description = "模型 ID") Long modelId,
        @Schema(description = "所属供应商 ID") Long providerId,
        @Schema(description = "所属供应商名称") String providerName,
        @Schema(description = "供应商模型 ID") String providerModelId,
        @Schema(description = "模型类型：CHAT 聊天模型 / VISION 图片识别") String modelType,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "能力标签，逗号分隔", nullable = true) String capabilities) {

    public static AiModelResponse from(AiModelEntity entity, String providerName) {
        return new AiModelResponse(entity.getId(), entity.getProviderId(), providerName,
                entity.getProviderModelId(), entity.getModelType(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getCapabilities());
    }
}
