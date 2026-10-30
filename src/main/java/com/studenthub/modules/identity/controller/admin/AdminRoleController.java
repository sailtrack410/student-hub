package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminRoleCopyRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleCreateRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleResponse;
import com.studenthub.modules.identity.controller.dto.AdminPermissionResponse;
import com.studenthub.modules.identity.controller.dto.AdminRoleDetailResponse;
import com.studenthub.modules.identity.controller.dto.AdminRolePermissionUpdateRequest;
import com.studenthub.modules.identity.controller.dto.AdminRoleUpdateRequest;
import com.studenthub.modules.identity.service.AdminRoleService;
import com.studenthub.modules.identity.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@Tag(name = "管理端-角色", description = "角色查询、新建/复制/删除与权限矩阵")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('system:user:manage','system:role:manage','platform:all')")
public class AdminRoleController {

    private final AdminUserService adminUserService;
    private final AdminRoleService adminRoleService;

    @GetMapping
    @Operation(summary = "查询启用中的角色列表", description = "供用户管理页的角色分配下拉使用。",
            operationId = "adminListRoles")
    public Result<List<AdminRoleResponse>> list() {
        return Result.success(adminUserService.listRoles());
    }

    @GetMapping("/permissions")
    @Operation(summary = "查询全部启用权限", operationId = "adminListPermissions")
    public Result<List<AdminPermissionResponse>> permissions() {
        return Result.success(adminRoleService.listPermissions());
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "查询角色详情与已授权限", operationId = "adminGetRoleDetail")
    public Result<AdminRoleDetailResponse> detail(@PathVariable Long roleId) {
        return Result.success(adminRoleService.detail(roleId));
    }

    @PutMapping("/{roleId}")
    @Operation(summary = "更新角色基本信息", operationId = "adminUpdateRole")
    public Result<AdminRoleDetailResponse> update(@PathVariable Long roleId,
                                                  @RequestBody @Valid AdminRoleUpdateRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.success(adminRoleService.updateRole(operator.userId(), roleId, request));
    }

    @PutMapping("/{roleId}/permissions")
    @Operation(summary = "整体覆盖角色权限", operationId = "adminUpdateRolePermissions")
    public Result<AdminRoleDetailResponse> updatePermissions(@PathVariable Long roleId,
                                                             @RequestBody @Valid AdminRolePermissionUpdateRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.success(adminRoleService.updatePermissions(operator.userId(), roleId, request.permissionIds()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:manage') or hasAuthority('platform:all')")
    @Operation(summary = "新建角色", description = "角色编码唯一且创建后不可修改；可同时指定初始权限，缺省空权限。",
            operationId = "adminCreateRole")
    public Result<AdminRoleDetailResponse> create(@RequestBody @Valid AdminRoleCreateRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.success(adminRoleService.createRole(operator.userId(), request));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('system:role:manage') or hasAuthority('platform:all')")
    @Operation(summary = "删除角色", description = "内置角色（平台管理员/申请人/服务人员/调度员）与仍被用户使用的角色拒绝删除；"
            + "删除为软删。", operationId = "adminDeleteRole")
    public Result<Void> delete(@PathVariable Long roleId,
                               @AuthenticationPrincipal AuthenticatedUser operator) {
        adminRoleService.deleteRole(operator.userId(), roleId);
        return Result.success();
    }

    @PostMapping("/{roleId}/copy")
    @PreAuthorize("hasAuthority('system:role:manage') or hasAuthority('platform:all')")
    @Operation(summary = "复制角色模板", description = "复制源角色权限集合生成新角色；编码/名称留空时自动生成。",
            operationId = "adminCopyRole")
    public Result<AdminRoleDetailResponse> copy(@PathVariable Long roleId,
                                                @RequestBody(required = false) AdminRoleCopyRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.success(adminRoleService.copyRole(operator.userId(), roleId, request));
    }
}
