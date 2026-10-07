package com.studenthub.modules.dashboard.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.dashboard.controller.dto.DashboardResponse;
import com.studenthub.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "管理端-工作台")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:user:manage') or hasAuthority('work-order:dispatch')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "工作台聚合数据", description = "KPI、近7天工单趋势、分类占比、最新工单。", operationId = "getDashboardOverview")
    public Result<DashboardResponse> overview() {
        return Result.success(dashboardService.overview());
    }
}
