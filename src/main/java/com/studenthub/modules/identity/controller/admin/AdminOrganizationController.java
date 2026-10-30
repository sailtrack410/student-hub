package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationLeaderCandidateResponse;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationRequest;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationResponse;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.AdminOrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/organizations")
@Tag(name = "管理端-组织架构", description = "组织树查询与维护；角色绑定的组织/数据范围依赖此数据")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('system:organization:manage')")
public class AdminOrganizationController {

    private final AdminOrganizationService adminOrganizationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "组织列表", description = "扁平列表（按 sortOrder/id 排序），附带 level、负责人、子组织数与用户数，"
            + "前端据此渲染树形缩进与删除保护提示。", operationId = "adminListOrganizations")
    public Result<List<AdminOrganizationResponse>> list() {
        return Result.success(adminOrganizationService.list());
    }

    @PostMapping
    @Operation(summary = "新建组织", operationId = "adminCreateOrganization")
    public Result<AdminOrganizationResponse> create(@RequestBody @Valid AdminOrganizationRequest request) {
        return Result.success(adminOrganizationService.create(currentUserProvider.currentUserIdOrSystem(), request));
    }

    @PutMapping("/{organizationId}")
    @Operation(summary = "更新组织", description = "支持修改父组织、负责人、排序与状态；不破坏既有编辑能力。",
            operationId = "adminUpdateOrganization")
    public Result<AdminOrganizationResponse> update(@PathVariable Long organizationId,
                                                    @RequestBody @Valid AdminOrganizationRequest request) {
        return Result.success(adminOrganizationService.update(
                currentUserProvider.currentUserIdOrSystem(), organizationId, request));
    }

    @DeleteMapping("/{organizationId}")
    @Operation(summary = "删除组织", description = "存在子组织或仍有用户（默认组织/角色绑定）时拒绝删除；删除为软删。",
            operationId = "adminDeleteOrganization")
    public Result<Void> delete(@PathVariable Long organizationId) {
        adminOrganizationService.delete(currentUserProvider.currentUserIdOrSystem(), organizationId);
        return Result.success();
    }

    @PostMapping("/{organizationId}/move")
    @Operation(summary = "同级上移/下移组织", description = "在同级兄弟中交换位置并重排 sortOrder；direction=UP/DOWN。",
            operationId = "adminMoveOrganization")
    public Result<Void> move(@PathVariable Long organizationId,
                             @RequestParam("direction") String direction) {
        adminOrganizationService.move(currentUserProvider.currentUserIdOrSystem(), organizationId, direction);
        return Result.success();
    }

    @GetMapping("/leader-candidates")
    @Operation(summary = "组织负责人候选", description = "当前校区启用中的用户，供组织负责人下拉选择；"
            + "避免为仅有组织管理权限的管理员开放用户列表权限。", operationId = "adminListOrganizationLeaderCandidates")
    public Result<List<AdminOrganizationLeaderCandidateResponse>> leaderCandidates() {
        return Result.success(adminOrganizationService.leaderCandidates());
    }
}
