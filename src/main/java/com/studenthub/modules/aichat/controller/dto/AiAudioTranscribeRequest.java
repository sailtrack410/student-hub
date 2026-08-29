package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "语音转写请求")
public record AiAudioTranscribeRequest(
        @Schema(description = "音频文件 ID（先经 /api/app/files 上传）")
        @NotNull Long fileId) {
}