package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserRequest;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserResponse;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserStatusRequest;
import com.studenthub.modules.community.service.CommunityAdvertiserService;
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
 * 管理端-社区广告主：按管理端校区上下文隔离的 CRUD 与启停。
 * 需 community:advertiser:manage 权限；广告创建时通过 advertiserId 关联广告主。
 */
@RestController
@RequestMapping("/api/admin/community/advertisers")
@Tag(name = "管理端-社区广告主", description = "广告主分页、创建、编辑、启停与删除")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:advertiser:manage')")
public class AdminCommunityAdvertiserController {

    private final CommunityAdvertiserService advertiserService;

    @GetMapping
    @Operation(summary = "广告主分页", description = "按关键词/状态过滤，按当前管理员校区隔离。",
            operationId = "adminPageCommunityAdvertisers")
    public Result<PageResult<AdminAdvertiserResponse>> page(AdminAdvertiserPageQuery query) {
        return Result.success(advertiserService.page(query));
    }

    @GetMapping("/{advertiserId}")
    @Operation(summary = "广告主详情", operationId = "adminGetCommunityAdvertiser")
    public Result<AdminAdvertiserResponse> detail(@PathVariable Long advertiserId) {
        return Result.success(advertiserService.detail(advertiserId));
    }

    @PostMapping
    @Operation(summary = "创建广告主", operationId = "adminCreateCommunityAdvertiser")
    public Result<AdminAdvertiserResponse> create(@RequestBody @Valid AdminAdvertiserRequest request) {
        return Result.success(advertiserService.create(request));
    }

    @PostMapping("/{advertiserId}")
    @Operation(summary = "编辑广告主", operationId = "adminUpdateCommunityAdvertiser")
    public Result<AdminAdvertiserResponse> update(@PathVariable Long advertiserId,
                                                  @RequestBody @Valid AdminAdvertiserRequest request) {
        return Result.success(advertiserService.update(advertiserId, request));
    }

    @PostMapping("/{advertiserId}/status")
    @Operation(summary = "启用/停用广告主", operationId = "adminUpdateCommunityAdvertiserStatus")
    public Result<AdminAdvertiserResponse> updateStatus(
            @PathVariable Long advertiserId,
            @RequestBody @Valid AdminAdvertiserStatusRequest request) {
        return Result.success(advertiserService.updateStatus(advertiserId, request.status()));
    }

    @DeleteMapping("/{advertiserId}")
    @Operation(summary = "删除广告主", description = "逻辑删除；已关联广告保留历史数据。",
            operationId = "adminDeleteCommunityAdvertiser")
    public Result<Void> delete(@PathVariable Long advertiserId) {
        advertiserService.delete(advertiserId);
        return Result.success();
    }
}
