package com.studenthub.modules.push.content.controller.admin;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.push.content.controller.dto.CreatorAppealHandleRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorCertifyRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorPageQuery;
import com.studenthub.modules.push.content.controller.dto.CreatorPenaltyRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorPenaltyResponse;
import com.studenthub.modules.push.content.controller.dto.CreatorProfileRequest;
import com.studenthub.modules.push.content.controller.dto.CreatorProfileResponse;
import com.studenthub.modules.push.content.service.CreatorService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-创作者运营（C10）：认证、等级、禁言/封禁与申诉处理。
 */
@RestController
@RequestMapping("/api/admin/creators")
@Tag(name = "管理端-创作者运营", description = "创作者认证、等级、处罚与申诉")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('creator:manage') or hasAuthority('community:moderate')")
public class AdminCreatorController {

    private final CreatorService creatorService;

    @GetMapping
    @Operation(summary = "创作者分页", description = "支持关键词（昵称/用户 ID）、认证状态、账号状态、等级过滤。",
            operationId = "adminPageCreators")
    public Result<PageResult<CreatorProfileResponse>> page(CreatorPageQuery query) {
        return Result.success(creatorService.page(query));
    }

    @GetMapping("/{creatorId}")
    @Operation(summary = "创作者详情", description = "包含全部处罚与申诉记录。",
            operationId = "adminGetCreator")
    public Result<CreatorProfileResponse> detail(@PathVariable Long creatorId) {
        return Result.success(creatorService.detail(creatorId));
    }

    @PostMapping
    @Operation(summary = "创建创作者档案", description = "用户需属于当前校区且不能重复建档。",
            operationId = "adminCreateCreator")
    public Result<CreatorProfileResponse> create(@RequestBody @Valid CreatorProfileRequest request) {
        return Result.success(creatorService.createProfile(request));
    }

    @PutMapping("/{creatorId}")
    @Operation(summary = "更新创作者档案", description = "可改昵称、等级、积分；状态由处罚派生，不可直接改。",
            operationId = "adminUpdateCreator")
    public Result<CreatorProfileResponse> update(@PathVariable Long creatorId,
                                                 @RequestBody @Valid CreatorProfileRequest request) {
        return Result.success(creatorService.updateProfile(creatorId, request));
    }

    @PostMapping("/{creatorId}/certify")
    @Operation(summary = "创作者认证审核", operationId = "adminCertifyCreator")
    public Result<CreatorProfileResponse> certify(@PathVariable Long creatorId,
                                                  @RequestBody @Valid CreatorCertifyRequest request) {
        return Result.success(creatorService.certify(creatorId, request));
    }

    @PostMapping("/{creatorId}/penalties")
    @Operation(summary = "新增处罚", description = "MUTE 需结束时间（缺省 24 小时）；BAN 留空表示永久。创建后自动重算账号状态。",
            operationId = "adminCreateCreatorPenalty")
    public Result<CreatorPenaltyResponse> penalize(@PathVariable Long creatorId,
                                                   @RequestBody @Valid CreatorPenaltyRequest request) {
        return Result.success(creatorService.createPenalty(creatorId, request));
    }

    @PostMapping("/penalties/{penaltyId}/revoke")
    @Operation(summary = "撤销处罚", description = "撤销后按剩余生效处罚重算账号状态。",
            operationId = "adminRevokeCreatorPenalty")
    public Result<CreatorPenaltyResponse> revoke(@PathVariable Long penaltyId) {
        return Result.success(creatorService.revokePenalty(penaltyId));
    }

    @PostMapping("/penalties/{penaltyId}/appeal")
    @Operation(summary = "处理申诉", description = "通过申诉会同时撤销对应处罚并重算账号状态。",
            operationId = "adminHandleCreatorAppeal")
    public Result<CreatorPenaltyResponse> appeal(@PathVariable Long penaltyId,
                                                 @RequestBody @Valid CreatorAppealHandleRequest request) {
        boolean approve = Boolean.TRUE.equals(request.approve());
        return Result.success(creatorService.handleAppeal(penaltyId, approve, request.result()));
    }
}
