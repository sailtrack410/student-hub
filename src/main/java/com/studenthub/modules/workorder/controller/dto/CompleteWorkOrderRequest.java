package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "工单办理完成请求")
public record CompleteWorkOrderRequest(
        @Schema(description = "办理结果说明") @NotBlank @Size(max = 512) String remark) {
}
