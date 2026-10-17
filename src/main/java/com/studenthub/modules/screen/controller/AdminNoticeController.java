package com.studenthub.modules.screen.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.screen.controller.dto.AdminNoticeResponse;
import com.studenthub.modules.screen.controller.dto.NoticeUpsertRequest;
import com.studenthub.modules.screen.service.ScreenAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notices")
@Tag(name = "管理端-公告管理", description = "终端大屏与 App 公告的发布与管理，支持定时发布")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final ScreenAdminService screenAdminService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "公告列表", description = "按渠道/状态/类型过滤，publishDate 倒序。",
            operationId = "adminListNotices")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('announcement:manage')")
    public Result<List<AdminNoticeResponse>> list(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String noticeType) {
        return Result.success(screenAdminService.list(channel, publishStatus, noticeType));
    }

    @PostMapping
    @Operation(summary = "发布公告", description = "action=PUBLISH 立即发布 / SCHEDULE 定时（publishAt 必填，到点自动上线）"
            + "/ DRAFT 存草稿 / OFFLINE 建档即下线；缺省 PUBLISH。", operationId = "adminCreateNotice")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('announcement:manage')")
    public Result<AdminNoticeResponse> create(@RequestBody @Valid NoticeUpsertRequest request) {
        return Result.success(screenAdminService.create(request, currentUserProvider.requireCurrentUser().userId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新公告", description = "全量覆盖编辑字段；action 可同时变更发布状态。",
            operationId = "adminUpdateNotice")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('announcement:manage')")
    public Result<AdminNoticeResponse> update(@PathVariable Long id, @RequestBody @Valid NoticeUpsertRequest request) {
        return Result.success(screenAdminService.update(id, request, currentUserProvider.requireCurrentUser().userId()));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "立即发布/恢复上线", operationId = "adminPublishNotice")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('announcement:manage')")
    public Result<AdminNoticeResponse> publish(@PathVariable Long id) {
        return Result.success(screenAdminService.changeStatus(id, "PUBLISHED"));
    }

    @PostMapping("/{id}/offline")
    @Operation(summary = "下线公告", description = "从终端轮播与 App 公告列表移除，数据保留可恢复。",
            operationId = "adminOfflineNotice")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('announcement:manage')")
    public Result<AdminNoticeResponse> offline(@PathVariable Long id) {
        return Result.success(screenAdminService.changeStatus(id, "OFFLINE"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告", description = "软删除，列表不再出现。", operationId = "adminDeleteNotice")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('announcement:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        screenAdminService.delete(id);
        return Result.success();
    }
}
