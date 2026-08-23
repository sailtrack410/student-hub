package com.studenthub.modules.identity.security;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "角色数据范围")
public record RoleScope(
        @Schema(description = "角色编码", example = "REQUESTER")
        String roleCode,
        @Schema(description = "角色所属组织 ID；全校或全平台角色可能为空", example = "10", nullable = true)
        Long organizationId,
        @Schema(description = "数据范围")
        DataScope dataScope) {
}
