package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "外部集成状态")
public record AdminIntegrationStatusResponse(
        @Schema(description = "集成编码") String code,
        @Schema(description = "集成名称") String name,
        @Schema(description = "状态：CONFIGURED/NOT_CONFIGURED/DISABLED/ERROR") String status,
        @Schema(description = "模式/说明") String mode,
        @Schema(description = "是否允许在页面修改") boolean editable,
        @Schema(description = "说明") String detail) {
}
