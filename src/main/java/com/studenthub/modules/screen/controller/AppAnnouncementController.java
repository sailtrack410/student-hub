package com.studenthub.modules.screen.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.screen.controller.dto.AppAnnouncementResponse;
import com.studenthub.modules.screen.service.ScreenAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 校园公告：管理员在 /api/admin/notices 以 channel=APP 发布的内容在此消费，
 * 只读已发布条目，登录态接口（与终端大屏免登录轮播隔离）。
 */
@RestController
@RequestMapping("/api/app/announcements")
@Tag(name = "小程序-校园公告", description = "App 端公告分页查询")
@RequiredArgsConstructor
public class AppAnnouncementController {

    private final ScreenAdminService screenAdminService;

    @GetMapping
    @Operation(summary = "分页查询校园公告", description = "仅返回 APP 渠道已发布公告，按发布日期倒序。",
            operationId = "pageAppAnnouncements")
    public Result<PageResult<AppAnnouncementResponse>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(screenAdminService.pageAppAnnouncements(pageNum, pageSize));
    }
}
