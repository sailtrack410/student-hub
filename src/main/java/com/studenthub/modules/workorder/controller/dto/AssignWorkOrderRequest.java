package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "派单请求")
public record AssignWorkOrderRequest(
        @Schema(description = "被指派服务人员用户 ID") @NotNull Long assigneeId,
        @Schema(description = "派单备注", nullable = true) @Size(max = 512) String comment) {
}
