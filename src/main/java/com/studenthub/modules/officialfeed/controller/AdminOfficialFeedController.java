package com.studenthub.modules.officialfeed.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedArticleResponse;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedArticleUpdateRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedCopyrightRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedImportRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedPreviewResponse;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedScheduleRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedSourceRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedSourceResponse;
import com.studenthub.modules.officialfeed.controller.dto.OfficialFeedSyncResult;
import com.studenthub.modules.officialfeed.entity.OfficialFeedArticleEntity;
import com.studenthub.modules.officialfeed.controller.dto.OfficialUserResponse;
import com.studenthub.modules.officialfeed.service.OfficialFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/official-feed")
@Tag(name = "管理端-官方通知", description = "学校官网通知抓取来源、手动同步与抓取记录")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate')")
public class AdminOfficialFeedController {

    private final OfficialFeedService officialFeedService;

    @GetMapping("/sources")
    @Operation(summary = "抓取来源列表", operationId = "listOfficialFeedSources")
    public Result<List<AdminOfficialFeedSourceResponse>> sources() {
        return Result.success(officialFeedService.listSources());
    }

    @GetMapping("/official-users")
    @Operation(summary = "可选官方发布主体", operationId = "listOfficialFeedUsers")
    public Result<List<OfficialUserResponse>> officialUsers() {
        return Result.success(officialFeedService.officialUsers());
    }

    @PostMapping("/sources")
    @Operation(summary = "新增抓取来源", operationId = "createOfficialFeedSource")
    public Result<AdminOfficialFeedSourceResponse> create(
            @RequestBody @Valid AdminOfficialFeedSourceRequest request) {
        return Result.success(officialFeedService.createSource(request));
    }

    @PutMapping("/sources/{sourceId}")
    @Operation(summary = "更新抓取来源", operationId = "updateOfficialFeedSource")
    public Result<AdminOfficialFeedSourceResponse> update(@PathVariable Long sourceId,
                                                          @RequestBody @Valid AdminOfficialFeedSourceRequest request) {
        return Result.success(officialFeedService.updateSource(sourceId, request));
    }

    @PostMapping("/sources/{sourceId}/sync")
    @Operation(summary = "立即同步单个来源", description = "同步拉取列表并抓取正文/图片，按规则发布为文章或动态。",
            operationId = "syncOfficialFeedSource")
    public Result<OfficialFeedSyncResult> sync(@PathVariable Long sourceId) {
        return Result.success(officialFeedService.sync(sourceId));
    }

    @PostMapping("/sync-all")
    @Operation(summary = "立即同步全部启用来源", operationId = "syncAllOfficialFeeds")
    public Result<List<OfficialFeedSyncResult>> syncAll() {
        return Result.success(officialFeedService.syncAllEnabled());
    }

    @GetMapping("/articles")
    @Operation(summary = "抓取记录分页", operationId = "pageOfficialFeedArticles")
    public Result<PageResult<AdminOfficialFeedArticleResponse>> articles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String status) {
        return Result.success(officialFeedService.pageArticles(pageNum, pageSize, sourceId, status));
    }
    // ------------------------------------------------------------------
    // C08：单篇 URL 导入 / 预览 / 编辑 / 定时 / 重试 / 版权确认
    // ------------------------------------------------------------------

    @PostMapping("/articles/import-url")
    @Operation(summary = "单篇 URL 导入（C08）",
            description = "仅允许 *.mycc.edu.cn；抓取标题/正文/图片后落草稿并返回预览（同一 URL 幂等返回已有记录）。",
            operationId = "importOfficialFeedArticleByUrl")
    public Result<AdminOfficialFeedPreviewResponse> importByUrl(
            @RequestBody @Valid AdminOfficialFeedImportRequest request) {
        return Result.success(officialFeedService.importByUrl(request));
    }

    @GetMapping("/articles/{articleId}/preview")
    @Operation(summary = "导入文章预览", description = "返回图文块（图片为短时签名地址）与正文字数、版权/定时状态。",
            operationId = "previewOfficialFeedArticle")
    public Result<AdminOfficialFeedPreviewResponse> preview(@PathVariable Long articleId) {
        return Result.success(officialFeedService.previewArticle(articleId));
    }

    @PutMapping("/articles/{articleId}")
    @Operation(summary = "编辑导入文章", description = "可改标题、正文文本与封面；图片块保留。已发布文章不可编辑。",
            operationId = "updateOfficialFeedArticle")
    public Result<AdminOfficialFeedPreviewResponse> updateArticle(
            @PathVariable Long articleId,
            @RequestBody @Valid AdminOfficialFeedArticleUpdateRequest request) {
        return Result.success(officialFeedService.updateArticle(articleId, request));
    }

    @PostMapping("/articles/{articleId}/copyright")
    @Operation(summary = "版权确认", description = "确认后才能发布/定时；未确认发布会被 400 拒绝。",
            operationId = "confirmOfficialFeedArticleCopyright")
    public Result<AdminOfficialFeedPreviewResponse> copyright(
            @PathVariable Long articleId,
            @RequestBody AdminOfficialFeedCopyrightRequest request) {
        return Result.success(officialFeedService.confirmCopyright(articleId, request.confirmed()));
    }

    @PostMapping("/articles/{articleId}/publish")
    @Operation(summary = "立即发布导入文章", description = "校验版权与正文后生成社区文章/动态。",
            operationId = "publishOfficialFeedArticle")
    public Result<AdminOfficialFeedPreviewResponse> publish(@PathVariable Long articleId) {
        return Result.success(officialFeedService.publishArticle(articleId));
    }

    @PostMapping("/articles/{articleId}/schedule")
    @Operation(summary = "定时发布导入文章", description = "到点由定时任务发布；需先确认版权。",
            operationId = "scheduleOfficialFeedArticle")
    public Result<AdminOfficialFeedPreviewResponse> schedule(
            @PathVariable Long articleId,
            @RequestBody @Valid AdminOfficialFeedScheduleRequest request) {
        return Result.success(officialFeedService.scheduleArticle(articleId, request.scheduledAt()));
    }

    @PostMapping("/articles/{articleId}/retry")
    @Operation(summary = "失败重试", description = "重新抓取原文并重建图文块；仍失败时保留 FAILED 状态与错误摘要，不伪造成功。",
            operationId = "retryOfficialFeedArticle")
    public Result<AdminOfficialFeedPreviewResponse> retry(@PathVariable Long articleId) {
        return Result.success(officialFeedService.retryArticle(articleId));
    }

}
