package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "退回工单完工确认请求")
public record RejectWorkOrderCompletionRequest(
        @Schema(description = "退回原因") @NotBlank @Size(max = 512) String reason) {
}
