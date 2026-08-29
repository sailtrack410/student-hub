package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "管理端调整用户角色请求（整体覆盖）")
public record AdminUserRoleUpdateRequest(
        @Schema(description = "角色 ID 全量列表，空列表表示清空角色")
        @NotEmpty List<Long> roleIds) {
}
