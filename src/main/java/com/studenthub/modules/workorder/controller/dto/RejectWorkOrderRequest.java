package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "调度驳回工单请求")
public record RejectWorkOrderRequest(
        @Schema(description = "驳回原因") @NotBlank @Size(max = 512) String reason) {
}
