package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "管理端调整用户角色绑定（含组织与数据范围，整体覆盖）")
public record AdminUserRoleBindingsRequest(
        @Schema(description = "角色绑定列表，空列表表示清空")
        @NotNull List<@Valid Binding> bindings) {

    @Schema(description = "单条角色绑定")
    public record Binding(
            @Schema(description = "角色 ID") Long roleId,
            @Schema(description = "组织 ID，可空") Long organizationId,
            @Schema(description = "数据范围 SELF/ASSIGNED/ORGANIZATION/ORGANIZATION_AND_CHILDREN/ALL") String dataScope) {
    }
}
