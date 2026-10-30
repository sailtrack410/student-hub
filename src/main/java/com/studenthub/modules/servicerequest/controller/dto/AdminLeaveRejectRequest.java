package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "请假驳回请求")
public record AdminLeaveRejectRequest(
        @Schema(description = "驳回原因") @NotBlank @Size(max = 512) String reason) {
}
