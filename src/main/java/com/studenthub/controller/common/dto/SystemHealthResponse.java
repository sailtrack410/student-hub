package com.studenthub.controller.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "应用健康状态")
public record SystemHealthResponse(
        @Schema(description = "服务名称", example = "student-hub-api")
        String service,
        @Schema(description = "服务状态，UP 表示正常", example = "UP", allowableValues = {"UP", "DOWN"})
        String status) {
}
