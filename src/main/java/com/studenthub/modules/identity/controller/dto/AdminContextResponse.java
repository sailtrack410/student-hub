package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "管理端当前会话与校区上下文")
public record AdminContextResponse(
        @Schema(description = "当前用户 ID") Long userId,
        @Schema(description = "当前用户显示名") String displayName,
        @Schema(description = "身份类型") String identityType,
        @Schema(description = "角色编码") List<String> roles,
        @Schema(description = "是否平台管理员") boolean platformAdmin,
        @Schema(description = "当前校区 ID") Long campusId,
        @Schema(description = "当前校区名称") String campusName,
        @Schema(description = "可切换校区") List<AdminCampusOptionResponse> campuses) {
}
