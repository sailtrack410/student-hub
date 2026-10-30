package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountPageQuery;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountResponse;
import com.studenthub.modules.community.controller.dto.AdminOfficialAccountStatusRequest;
import com.studenthub.modules.community.service.CommunityOfficialAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-社区官方账号：官方抓取号/人工运营号/广告服务号的创建、编辑与启停。
 * 账号类型规则由 Service 校验：已绑定抓取来源的账号必须保持 OFFICIAL_AUTO。
 */
@RestController
@RequestMapping("/api/admin/community/official-accounts")
@Tag(name = "管理端-社区官方账号", description = "账号类型、运营人、展示名、简介、认证状态与启停")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate') "
        + "or hasAuthority('community:official:manage')")
public class AdminCommunityOfficialAccountController {

    private final CommunityOfficialAccountService officialAccountService;

    @GetMapping
    @Operation(summary = "官方账号分页", description = "仅返回官方类账号（OFFICIAL_AUTO/OFFICIAL_HUMAN/AD_SERVICE），按当前管理员校区隔离。",
            operationId = "adminPageOfficialAccounts")
    public Result<PageResult<AdminOfficialAccountResponse>> page(AdminOfficialAccountPageQuery query) {
        return Result.success(officialAccountService.page(query));
    }

    @PostMapping
    @Operation(summary = "创建官方账号", operationId = "adminCreateOfficialAccount")
    public Result<AdminOfficialAccountResponse> create(
            @RequestBody @Valid AdminOfficialAccountRequest request) {
        return Result.success(officialAccountService.create(request));
    }

    @PostMapping("/{userId}")
    @Operation(summary = "编辑官方账号", description = "账号类型、运营人、展示名、简介、认证状态与启停；用户名不可修改。",
            operationId = "adminUpdateOfficialAccount")
    public Result<AdminOfficialAccountResponse> update(
            @PathVariable Long userId,
            @RequestBody @Valid AdminOfficialAccountRequest request) {
        return Result.success(officialAccountService.update(userId, request));
    }

    @PostMapping("/{userId}/status")
    @Operation(summary = "启用/停用官方账号", operationId = "adminUpdateOfficialAccountStatus")
    public Result<AdminOfficialAccountResponse> updateStatus(
            @PathVariable Long userId,
            @RequestBody @Valid AdminOfficialAccountStatusRequest request) {
        return Result.success(officialAccountService.updateStatus(userId, request.status()));
    }
}
