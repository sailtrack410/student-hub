package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "撤销服务申请请求")
public record ServiceRequestCancelRequest(
        @Schema(description = "撤销原因") @NotBlank @Size(max = 512) String reason) {
}
