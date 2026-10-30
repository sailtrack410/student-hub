package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "改派工单请求")
public record ReassignWorkOrderRequest(
        @Schema(description = "新处理人（服务人员）ID") @NotNull(message = "新处理人必填") Long assigneeId,
        @Schema(description = "改派说明", nullable = true) @Size(max = 512, message = "改派说明最长 512 字") String comment) {
}
