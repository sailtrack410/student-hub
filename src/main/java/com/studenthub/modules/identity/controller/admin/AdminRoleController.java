package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminRoleResponse;
import com.studenthub.modules.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@Tag(name = "管理端-角色", description = "启用角色查询")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('system:user:manage')")
public class AdminRoleController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "查询启用中的角色列表", description = "供用户管理页的角色分配下拉使用。",
            operationId = "adminListRoles")
    public Result<List<AdminRoleResponse>> list() {
        return Result.success(adminUserService.listRoles());
    }
}
