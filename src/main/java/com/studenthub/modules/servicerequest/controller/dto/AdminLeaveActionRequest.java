package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "请假通过/销假请求")
public record AdminLeaveActionRequest(
        @Schema(description = "审批意见或销假备注") @Size(max = 512) String comment) {
}
