package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminDirectAdPageQuery;
import com.studenthub.modules.community.controller.dto.AdminDirectAdRequest;
import com.studenthub.modules.community.controller.dto.AdminDirectAdResponse;
import com.studenthub.modules.community.controller.dto.AdminDirectAdReviewRequest;
import com.studenthub.modules.community.controller.dto.AdminDirectAdStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminDirectAdStatusRequest;
import com.studenthub.modules.community.service.CommunityDirectAdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端-官方直投广告：CRUD、上下架、优先级与曝光/点击/跳出率统计。
 * 统计中外部浏览器/DEEPLINK 不可测跳出，跳出率返回 null，不伪造 0。
 */
@RestController
@RequestMapping("/api/admin/community/direct-ads")
@Tag(name = "管理端-官方直投广告", description = "直投广告管理、上下架与数据统计")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate') "
        + "or hasAuthority('community:ad:manage')")
public class AdminCommunityAdController {

    private final CommunityDirectAdService directAdService;

    @GetMapping
    @Operation(summary = "广告分页", description = "按状态/广告位/关键词过滤，按优先级倒序。",
            operationId = "adminPageDirectAds")
    public Result<PageResult<AdminDirectAdResponse>> page(AdminDirectAdPageQuery query) {
        return Result.success(directAdService.page(query));
    }

    @GetMapping("/{adId}")
    @Operation(summary = "广告详情", operationId = "adminGetDirectAd")
    public Result<AdminDirectAdResponse> detail(@PathVariable Long adId) {
        return Result.success(directAdService.detail(adId));
    }

    @PostMapping
    @Operation(summary = "创建广告", operationId = "adminCreateDirectAd")
    public Result<AdminDirectAdResponse> create(@RequestBody @Valid AdminDirectAdRequest request) {
        return Result.success(directAdService.create(request));
    }

    @PostMapping("/{adId}")
    @Operation(summary = "编辑广告", description = "可调整内容、投放时间窗、优先级与状态。",
            operationId = "adminUpdateDirectAd")
    public Result<AdminDirectAdResponse> update(@PathVariable Long adId,
                                                @RequestBody @Valid AdminDirectAdRequest request) {
        return Result.success(directAdService.update(adId, request));
    }

    @PostMapping("/{adId}/status")
    @Operation(summary = "广告上下架", description = "ENABLED 上架（结束时间必须晚于当前时间）/ DISABLED 下架 / DRAFT 草稿。",
            operationId = "adminUpdateDirectAdStatus")
    public Result<AdminDirectAdResponse> updateStatus(@PathVariable Long adId,
                                                      @RequestBody @Valid AdminDirectAdStatusRequest request) {
        return Result.success(directAdService.updateStatus(adId, request.status()));
    }

    @PostMapping("/{adId}/submit")
    @Operation(summary = "提交广告审核",
            description = "草稿/已驳回广告提交审核后进入 PENDING；重复提交 PENDING 幂等返回。",
            operationId = "adminSubmitDirectAdReview")
    public Result<AdminDirectAdResponse> submit(@PathVariable Long adId) {
        return Result.success(directAdService.submit(adId));
    }

    @PostMapping("/{adId}/review")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:ad:review')")
    @Operation(summary = "审核广告",
            description = "仅 PENDING 广告可审核；APPROVE 通过，REJECT 驳回且必须填写原因。需 community:ad:review 权限。",
            operationId = "adminReviewDirectAd")
    public Result<AdminDirectAdResponse> review(@PathVariable Long adId,
                                                @RequestBody @Valid AdminDirectAdReviewRequest request) {
        return Result.success(directAdService.review(adId, request));
    }

    @DeleteMapping("/{adId}")
    @Operation(summary = "删除广告", description = "逻辑删除；已产生的统计数据保留。",
            operationId = "adminDeleteDirectAd")
    public Result<Void> delete(@PathVariable Long adId) {
        directAdService.delete(adId);
        return Result.success();
    }

    @GetMapping("/{adId}/stats")
    @Operation(summary = "广告数据统计",
            description = "曝光、点击、CTR、可测落地数、跳出率与平均停留；bounceThresholdSeconds 可传 3/5/8/10 按不同 N 重算，"
                    + "外部浏览器/Deeplink 的跳出率与平均停留返回 null。",
            operationId = "adminGetDirectAdStats")
    public Result<AdminDirectAdStatsResponse> stats(
            @PathVariable Long adId,
            @RequestParam(required = false) Integer bounceThresholdSeconds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        return Result.success(directAdService.stats(adId, bounceThresholdSeconds, startAt, endAt));
    }
}
