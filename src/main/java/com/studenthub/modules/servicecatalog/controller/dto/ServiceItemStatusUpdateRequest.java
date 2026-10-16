package com.studenthub.modules.servicecatalog.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "管理端更新服务项状态请求")
public record ServiceItemStatusUpdateRequest(
        @Schema(description = "目标状态：ENABLED 已上线 / DISABLED 已停用")
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED 或 DISABLED")
        String status) {
}
