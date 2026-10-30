package com.studenthub.modules.community.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdClickResponse;
import com.studenthub.modules.community.controller.dto.AdImpressionResponse;
import com.studenthub.modules.community.controller.dto.AdLandingExitResponse;
import com.studenthub.modules.community.controller.dto.AdLandingResponse;
import com.studenthub.modules.community.controller.dto.ReportAdClickRequest;
import com.studenthub.modules.community.controller.dto.ReportAdImpressionRequest;
import com.studenthub.modules.community.controller.dto.ReportAdLandingExitRequest;
import com.studenthub.modules.community.controller.dto.ReportAdLandingLoadedRequest;
import com.studenthub.modules.community.service.CommunityAdFeedService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 官方直投广告事件上报：曝光 → 点击（返回 clickId）→ 落地加载完成 → 停留/跳出。
 * 外部浏览器/DEEPLINK 只能记录点击，落地/停留上报会被拒绝。
 */
@RestController
@RequestMapping("/api/app/community/direct-ads")
@Tag(name = "小程序-直投广告事件", description = "曝光、点击、落地与停留/跳出上报")
@RequiredArgsConstructor
public class AppCommunityDirectAdController {

    private final CommunityAdFeedService adFeedService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/{adId}/impression")
    @Operation(summary = "广告曝光上报", description = "同一 clickId 只记一次，幂等。",
            operationId = "reportCommunityAdImpression")
    public Result<AdImpressionResponse> impression(
            @PathVariable Long adId,
            @RequestBody(required = false) @Valid ReportAdImpressionRequest request) {
        ReportAdImpressionRequest body = request == null ? new ReportAdImpressionRequest(null, null) : request;
        return Result.success(adFeedService.reportImpression(
                currentUserProvider.requireCurrentUser(), adId, body));
    }

    @PostMapping("/{adId}/click")
    @Operation(summary = "广告点击上报", description = "返回 clickId、跳转配置与是否可测跳出；外部浏览器/Deeplink 也能记录点击。",
            operationId = "reportCommunityAdClick")
    public Result<AdClickResponse> click(
            @PathVariable Long adId,
            @RequestBody(required = false) @Valid ReportAdClickRequest request) {
        ReportAdClickRequest body = request == null ? new ReportAdClickRequest(null, null) : request;
        return Result.success(adFeedService.reportClick(
                currentUserProvider.requireCurrentUser(), adId, body));
    }

    @PostMapping("/landing/loaded")
    @Operation(summary = "落地页加载完成上报", description = "仅 IN_APP_WEBVIEW/APP_PAGE 可测；用于计算可测落地数。",
            operationId = "reportCommunityAdLandingLoaded")
    public Result<AdLandingResponse> landingLoaded(
            @RequestBody @Valid ReportAdLandingLoadedRequest request) {
        return Result.success(adFeedService.reportLandingLoaded(
                currentUserProvider.requireCurrentUser(), request));
    }

    @PostMapping("/landing/exit")
    @Operation(summary = "落地页退出上报（停留/跳出）",
            description = "服务端按广告配置的 N 秒计算 bounced；原始 dwellMs 入库，管理端统计可按不同 N 重算。",
            operationId = "reportCommunityAdLandingExit")
    public Result<AdLandingExitResponse> landingExit(
            @RequestBody @Valid ReportAdLandingExitRequest request) {
        return Result.success(adFeedService.reportLandingExit(
                currentUserProvider.requireCurrentUser(), request));
    }
}
