package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端可切换校区")
public record AdminCampusOptionResponse(
        @Schema(description = "校区 ID") Long campusId,
        @Schema(description = "校区编码") String campusCode,
        @Schema(description = "校区名称") String campusName,
        @Schema(description = "状态") String status) {
}
