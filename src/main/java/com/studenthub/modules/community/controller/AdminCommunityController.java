package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminCommunityContentActionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportPageQuery;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityReportSummaryResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunitySanctionRequest;
import com.studenthub.modules.community.service.CommunityModerationService;
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
 * 管理端社区治理：举报队列、内容下架/恢复、用户处罚。
 * 举报处理与处置动作都会记录操作人与处理说明，作为校方追溯与申诉依据。
 */
@RestController
@RequestMapping("/api/admin/community")
@Tag(name = "管理端-社区治理")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate')")
public class AdminCommunityController {

    private final CommunityModerationService communityModerationService;

    @GetMapping("/reports")
    @Operation(summary = "分页查询社区举报", description = "可按状态与举报对象类型过滤，默认按待处理优先、举报时间倒序。", operationId = "pageAdminCommunityReports")
    public Result<PageResult<AdminCommunityReportResponse>> reports(AdminCommunityReportPageQuery query) {
        return Result.success(communityModerationService.pageReports(query));
    }

    @GetMapping("/reports/summary")
    @Operation(summary = "查询社区治理概览", description = "举报量（待处理/已成立/已驳回/今日新增）与当前生效处罚数。", operationId = "getAdminCommunityReportSummary")
    public Result<AdminCommunityReportSummaryResponse> summary() {
        return Result.success(communityModerationService.summary());
    }

    @PostMapping("/reports/{reportId}/handle")
    @Operation(summary = "处理社区举报", description = "ACCEPT 举报成立可同时下架/删除内容并处罚作者；REJECT 仅记录结论。处理结果站内通知举报人。", operationId = "handleAdminCommunityReport")
    public Result<Void> handle(@PathVariable Long reportId,
                              @RequestBody @Valid AdminCommunityReportHandleRequest request) {
        communityModerationService.handleReport(reportId, request);
        return Result.success();
    }

    @PostMapping("/content/{targetType}/{contentId}/hide")
    @Operation(summary = "下架帖子或评论", description = "治理端日常巡检直接处置：内容保留、仅治理端可见，可由恢复接口还原，并通知作者。", operationId = "hideAdminCommunityContent")
    public Result<Void> hide(@PathVariable String targetType, @PathVariable Long contentId,
                            @RequestBody(required = false) AdminCommunityContentActionRequest request) {
        communityModerationService.hideContent(targetType, contentId, request);
        return Result.success();
    }

    @PostMapping("/content/{targetType}/{contentId}/restore")
    @Operation(summary = "恢复已下架的帖子或评论", description = "把 HIDDEN 状态内容恢复为正常展示，并通知作者。", operationId = "restoreAdminCommunityContent")
    public Result<Void> restore(@PathVariable String targetType, @PathVariable Long contentId,
                               @RequestBody(required = false) AdminCommunityContentActionRequest request) {
        communityModerationService.restoreContent(targetType, contentId, request);
        return Result.success();
    }

    @PostMapping("/users/{userId}/sanctions")
    @Operation(summary = "处罚社区用户", description = "MUTE 限制发言 / BAN 禁止访问社区；hours 不传或 0 表示长期有效，处罚即时通知本人。", operationId = "sanctionAdminCommunityUser")
    public Result<Void> sanction(@PathVariable Long userId,
                                @RequestBody @Valid AdminCommunitySanctionRequest request) {
        communityModerationService.sanctionUser(userId, request);
        return Result.success();
    }

    @PostMapping("/sanctions/{sanctionId}/revoke")
    @Operation(summary = "撤销社区处罚", description = "用于申诉通过后的提前解除，撤销后通知本人。", operationId = "revokeAdminCommunitySanction")
    public Result<Void> revoke(@PathVariable Long sanctionId) {
        communityModerationService.revokeSanction(sanctionId);
        return Result.success();
    }
}
