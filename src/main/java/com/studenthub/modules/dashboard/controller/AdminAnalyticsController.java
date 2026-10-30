package com.studenthub.modules.dashboard.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.dashboard.controller.dto.AnalyticsOverviewResponse;
import com.studenthub.modules.dashboard.service.AnalyticsService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@Tag(name = "管理端-数据分析", description = "服务申请/工单/社区/用户/AI 五类指标与趋势")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;
    private final AdminCampusContextService campusContextService;

    @GetMapping("/overview")
    @Operation(summary = "数据分析总览", description = "days 仅支持 7 或 30；按当前管理端校区统计。",
            operationId = "adminGetAnalyticsOverview")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('analytics:view')")
    public Result<AnalyticsOverviewResponse> overview(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Long campusId) {
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "days 仅支持 7 或 30");
        }
        // 与现有管理端一致：默认使用 X-Campus-Id 上下文；显式传 campusId 时按权限切换（拦截器负责清理）。
        if (campusId != null && campusId > 0) {
            campusContextService.activate(campusId);
        }
        return Result.success(analyticsService.overview(days));
    }
}
