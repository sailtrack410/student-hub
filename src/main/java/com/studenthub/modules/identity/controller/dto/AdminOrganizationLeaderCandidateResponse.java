package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组织负责人候选（U03）：当前校区启用中的用户，供组织负责人下拉选择。
 *
 * <p>单独提供轻量接口，避免给只拥有 organization:manage 的管理员开放用户列表权限。</p>
 */
@Schema(description = "组织负责人候选")
public record AdminOrganizationLeaderCandidateResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "默认组织 ID", nullable = true) Long defaultOrganizationId,
        @Schema(description = "默认组织名称", nullable = true) String defaultOrganizationName) {
}
