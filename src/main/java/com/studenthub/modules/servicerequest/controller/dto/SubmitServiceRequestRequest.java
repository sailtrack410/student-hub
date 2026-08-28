package com.studenthub.modules.servicerequest.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "提交服务申请请求")
public record SubmitServiceRequestRequest(
        @Schema(description = "服务项 ID") @NotNull Long itemId,
        @Schema(description = "申请标题，不填默认使用服务项名称", nullable = true) @Size(max = 128) String title,
        @Schema(description = "表单内容，字段以服务项详情的 formSchema 为准") JsonNode formData) {
}
