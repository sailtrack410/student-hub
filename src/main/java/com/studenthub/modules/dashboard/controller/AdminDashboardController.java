package com.studenthub.modules.dashboard.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.dashboard.controller.dto.DashboardResponse;
import com.studenthub.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "管理端-工作台", description = "管理端聚合工作台数据")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:user:manage') or hasAuthority('work-order:dispatch')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "工作台聚合数据",
            description = "KPI、今日待办汇总、快捷入口、日期范围内工单趋势/分类占比/最新工单；日期范围默认近 7 天，最长 31 天。",
            operationId = "getDashboardOverview")
    public Result<DashboardResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(dashboardService.overview(startDate, endDate));
    }
}
