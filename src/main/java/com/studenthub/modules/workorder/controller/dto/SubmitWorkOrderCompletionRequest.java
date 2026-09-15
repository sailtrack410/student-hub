package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "提交工单完工照片请求")
public record SubmitWorkOrderCompletionRequest(
        @Schema(description = "完工照片文件 ID") @NotNull Long fileId,
        @Schema(description = "办理结果说明") @NotBlank @Size(max = 512) String remark) {
}
