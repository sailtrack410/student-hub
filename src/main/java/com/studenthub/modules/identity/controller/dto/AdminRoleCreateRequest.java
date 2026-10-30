package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理端新建角色（U02）：角色编码创建后不可修改，权限可选（也可先建空角色再配置）。
 */
@Schema(description = "管理端新建角色请求")
public record AdminRoleCreateRequest(
        @Schema(description = "角色编码，仅字母/数字/下划线/短横线，创建后不可修改")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,63}$", message = "角色编码需以大写字母开头，仅允许 A-Z 0-9 _ -")
        String roleCode,
        @Schema(description = "角色名称") @NotBlank @Size(max = 128) String roleName,
        @Schema(description = "角色类型：ADMIN/REQUESTER/WORKER/DISPATCHER") @NotBlank @Size(max = 32) String roleType,
        @Schema(description = "状态 ENABLED/DISABLED，缺省 ENABLED") String status,
        @Schema(description = "初始权限 ID 列表，可为空", nullable = true) List<Long> permissionIds) {
}
