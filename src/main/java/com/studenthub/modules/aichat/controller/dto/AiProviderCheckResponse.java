package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "供应商连通性检查结果")
public record AiProviderCheckResponse(
        @Schema(description = "是否通过") boolean ok,
        @Schema(description = "结果说明") String result,
        @Schema(description = "检查时间") LocalDateTime checkedAt) {
}
