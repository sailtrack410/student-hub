package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminAdPlanPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdPlanRequest;
import com.studenthub.modules.community.controller.dto.AdminAdPlanResponse;
import com.studenthub.modules.community.controller.dto.AdminAdPlanStatusRequest;
import com.studenthub.modules.community.service.CommunityAdLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-广告投放计划（C13）：一个计划下可挂多条直投广告，统一排期/预算/状态。
 */
@RestController
@RequestMapping("/api/admin/community/ad-plans")
@Tag(name = "管理端-投放计划", description = "投放计划 CRUD、启停与分页")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate') "
        + "or hasAuthority('community:ad:manage')")
public class AdminCommunityAdPlanController {

    private final CommunityAdLibraryService adLibraryService;

    @GetMapping
    @Operation(summary = "投放计划分页", description = "按状态/广告位/广告主/关键词过滤，附计划下直投广告数与展示状态。",
            operationId = "adminPageAdPlans")
    public Result<PageResult<AdminAdPlanResponse>> page(AdminAdPlanPageQuery query) {
        return Result.success(adLibraryService.pagePlans(query));
    }

    @PostMapping
    @Operation(summary = "创建投放计划", description = "新建计划默认草稿；排期结束时间必须晚于开始时间。",
            operationId = "adminCreateAdPlan")
    public Result<AdminAdPlanResponse> create(@RequestBody @Valid AdminAdPlanRequest request) {
        return Result.success(adLibraryService.createPlan(request));
    }

    @PostMapping("/{planId}")
    @Operation(summary = "更新投放计划", operationId = "adminUpdateAdPlan")
    public Result<AdminAdPlanResponse> update(@PathVariable Long planId,
                                              @RequestBody @Valid AdminAdPlanRequest request) {
        return Result.success(adLibraryService.updatePlan(planId, request));
    }

    @PostMapping("/{planId}/status")
    @Operation(summary = "投放计划启停", description = "ENABLED 要求结束时间晚于当前时间；DISABLED/DRAFT 直接生效。",
            operationId = "adminUpdateAdPlanStatus")
    public Result<AdminAdPlanResponse> updateStatus(@PathVariable Long planId,
                                                    @RequestBody @Valid AdminAdPlanStatusRequest request) {
        return Result.success(adLibraryService.updatePlanStatus(planId, request.status()));
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "删除投放计划", description = "计划下仍有直投广告时拒绝删除，避免悬空引用。",
            operationId = "adminDeleteAdPlan")
    public Result<Void> delete(@PathVariable Long planId) {
        adLibraryService.deletePlan(planId);
        return Result.success();
    }
}
