package com.studenthub.modules.servicecatalog.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务项受理组织选项")
public record ServiceHandlerOrganizationResponse(
        @Schema(description = "组织 ID") Long organizationId,
        @Schema(description = "组织名称") String organizationName,
        @Schema(description = "组织编码", nullable = true) String organizationCode,
        @Schema(description = "状态") String status) {
}
