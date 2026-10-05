package com.studenthub.modules.home.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.home.controller.dto.HomeDashboardResponse;
import com.studenthub.modules.home.service.HomeDashboardService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页 BFF（API需求清单 4.1）：冷启动单请求聚合，替代前端五连发。
 */
@RestController
@RequestMapping("/api/app/home")
@Tag(name = "小程序-首页聚合", description = "首页综合仪表盘（BFF）")
@RequiredArgsConstructor
public class AppHomeDashboardController {

    private final HomeDashboardService homeDashboardService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/dashboard")
    @Operation(summary = "首页综合仪表盘", description = "一次聚合：问候语、学业信息条（校区/星期/周次）、下一节课、最新公告、进行中申请、未读数；单项失败各自兜底不影响整体。",
            operationId = "getHomeDashboard")
    public Result<HomeDashboardResponse> dashboard() {
        return Result.success(homeDashboardService.dashboard(currentUserProvider.requireCurrentUser()));
    }
}
