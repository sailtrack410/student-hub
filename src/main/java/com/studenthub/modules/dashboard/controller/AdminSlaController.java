package com.studenthub.modules.dashboard.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.dashboard.controller.dto.SlaOverviewResponse;
import com.studenthub.modules.dashboard.service.SlaService;
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
@RequestMapping("/api/admin/sla")
@Tag(name = "管理端-SLA 看板", description = "按服务项/组织聚合承诺时长、超时率、平均办结、满意度与返工/驳回")
@RequiredArgsConstructor
public class AdminSlaController {

    private final SlaService slaService;

    @GetMapping("/overview")
    @Operation(summary = "SLA 看板", description = "日期范围默认近 30 天，最长 92 天；截止时间按服务承诺与工作日历推算。",
            operationId = "adminGetSlaOverview")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('sla:view')")
    public Result<SlaOverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(slaService.overview(startDate, endDate));
    }
}
