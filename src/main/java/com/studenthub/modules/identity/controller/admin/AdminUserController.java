package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminUserAuthorizationRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserPageQuery;
import com.studenthub.modules.identity.controller.dto.AdminUserRoleUpdateRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserStatusRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserSummaryResponse;
import com.studenthub.modules.identity.controller.dto.WorkerCandidateResponse;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/users")
@Tag(name = "管理端-用户管理")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('system:user:manage')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "分页查询用户", description = "支持关键词（用户名/昵称/手机号）与状态、身份、授权状态、角色过滤。",
            operationId = "adminPageUsers")
    public Result<PageResult<AdminUserSummaryResponse>> page(AdminUserPageQuery query) {
        return Result.success(adminUserService.page(query));
    }

    @PostMapping("/{userId}/status")
    @Operation(summary = "启用/停用账号", description = "停用后该用户全部在线会话立即失效；不能操作自己。",
            operationId = "adminUpdateUserStatus")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @RequestBody @Valid AdminUserStatusRequest request) {
        adminUserService.updateStatus(currentUserProvider.currentUserIdOrSystem(), userId, request.status());
        return Result.success();
    }

    @PostMapping("/{userId}/authorization")
    @Operation(summary = "授权审核", description = "APPROVE 置为已授权，REJECT 置为已拒绝，用户在线会话原地刷新。",
            operationId = "adminReviewUserAuthorization")
    public Result<Void> reviewAuthorization(@PathVariable Long userId,
                                            @RequestBody @Valid AdminUserAuthorizationRequest request) {
        adminUserService.reviewAuthorization(currentUserProvider.currentUserIdOrSystem(), userId,
                request.action(), request.reason());
        return Result.success();
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "调整用户角色", description = "整体覆盖角色绑定；授予服务角色会联动身份为后勤并标记已授权，"
            + "撤销后回落；用户在线会话原地刷新。", operationId = "adminUpdateUserRoles")
    public Result<AdminUserSummaryResponse> updateRoles(@PathVariable Long userId,
                                                        @RequestBody @Valid AdminUserRoleUpdateRequest request) {
        return Result.success(adminUserService.updateRoles(currentUserProvider.currentUserIdOrSystem(),
                userId, request.roleIds()));
    }

    @GetMapping("/workers")
    @Operation(summary = "查询可派单的服务人员", description = "返回绑定服务人员角色且账号启用的用户，支持关键词过滤。",
            operationId = "adminListWorkerCandidates")
    public Result<List<WorkerCandidateResponse>> workers(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(adminUserService.workerCandidates(keyword));
    }
}
