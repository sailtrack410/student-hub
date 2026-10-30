package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminBannedWordImportResult;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchPostStatusRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBatchResult;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionReorderRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicMergeRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminModerationTemplateRequest;
import com.studenthub.modules.community.controller.dto.AdminModerationTemplateResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityAppealHandleRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityAppealResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityBannedWordRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityBannedWordResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityPostDetailResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityPostResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunitySectionResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityStatsResponse;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicRequest;
import com.studenthub.modules.community.controller.dto.AdminCommunityTopicResponse;
import com.studenthub.modules.community.service.CommunityAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理端-社区运营：社区数据概览、帖子管理、分区/话题、屏蔽词、申诉复核。
 * 与 AdminCommunityController（举报与处置）互补，共同构成社区管理项。
 */
@RestController
@RequestMapping("/api/admin/community")
@Tag(name = "管理端-社区运营", description = "社区概览/帖子/分区/话题/屏蔽词/申诉")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate')")
public class AdminCommunityManageController {

    private final CommunityAdminService communityAdminService;

    @GetMapping("/overview")
    @Operation(summary = "社区数据概览", description = "days=7/30（默认 7）：帖子/评论/活跃用户/举报/隐藏/申诉/屏蔽词，"
            + "趋势、分区、话题 Top N 均按所选区间统计；举报/申诉/隐藏为当前快照。",
            operationId = "adminCommunityOverview")
    public Result<AdminCommunityStatsResponse> overview(
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(communityAdminService.stats(days));
    }

    @GetMapping("/overview/export")
    @Operation(summary = "社区概览 CSV 导出", description = "days=7/30（默认 7）；UTF-8 + BOM，含时间范围、KPI、"
            + "每日趋势、分区、话题 Top N 与口径说明。", operationId = "adminCommunityOverviewExport")
    public ResponseEntity<byte[]> exportOverview(
            @RequestParam(defaultValue = "7") int days) {
        byte[] csv = communityAdminService.overviewCsv(days);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"community-overview-" + days + "d.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/posts")
    @Operation(summary = "帖子管理列表", description = "按分区/状态/类型/关键字过滤，带封面图与举报数。",
            operationId = "adminCommunityPosts")
    public Result<PageResult<AdminCommunityPostResponse>> posts(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String postType) {
        return Result.success(communityAdminService.pagePosts(pageNum, pageSize, keyword, sectionId, status, postType));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "帖子详情（管理端）", description = "含完整图文块、隐藏原因与举报数。",
            operationId = "adminCommunityPostDetail")
    public Result<AdminCommunityPostDetailResponse> postDetail(@PathVariable Long postId) {
        return Result.success(communityAdminService.postDetail(postId));
    }

    @GetMapping("/sections")
    @Operation(summary = "分区列表（含停用与帖子数）", operationId = "adminCommunitySections")
    public Result<List<AdminCommunitySectionResponse>> sections() {
        return Result.success(communityAdminService.sections());
    }

    @PostMapping("/sections")
    @Operation(summary = "创建分区", operationId = "adminCommunityCreateSection")
    public Result<AdminCommunitySectionResponse> createSection(
            @RequestBody @Valid AdminCommunitySectionRequest request) {
        return Result.success(communityAdminService.createSection(request));
    }

    @PutMapping("/sections/{id}")
    @Operation(summary = "更新分区", description = "分区编码创建后不可修改。", operationId = "adminCommunityUpdateSection")
    public Result<AdminCommunitySectionResponse> updateSection(
            @PathVariable Long id, @RequestBody @Valid AdminCommunitySectionRequest request) {
        return Result.success(communityAdminService.updateSection(id, request));
    }

    @DeleteMapping("/sections/{id}")
    @Operation(summary = "删除分区", description = "分区下还有帖子时拒绝删除。", operationId = "adminCommunityDeleteSection")
    public Result<Void> deleteSection(@PathVariable Long id) {
        communityAdminService.deleteSection(id);
        return Result.success();
    }

    @GetMapping("/topics")
    @Operation(summary = "话题列表", operationId = "adminCommunityTopics")
    public Result<List<AdminCommunityTopicResponse>> topics() {
        return Result.success(communityAdminService.topics());
    }

    @PostMapping("/topics")
    @Operation(summary = "创建话题", operationId = "adminCommunityCreateTopic")
    public Result<AdminCommunityTopicResponse> createTopic(
            @RequestBody @Valid AdminCommunityTopicRequest request) {
        return Result.success(communityAdminService.createTopic(request));
    }

    @PutMapping("/topics/{id}")
    @Operation(summary = "更新话题", operationId = "adminCommunityUpdateTopic")
    public Result<AdminCommunityTopicResponse> updateTopic(
            @PathVariable Long id, @RequestBody @Valid AdminCommunityTopicRequest request) {
        return Result.success(communityAdminService.updateTopic(id, request));
    }

    @DeleteMapping("/topics/{id}")
    @Operation(summary = "删除话题", operationId = "adminCommunityDeleteTopic")
    public Result<Void> deleteTopic(@PathVariable Long id) {
        communityAdminService.deleteTopic(id);
        return Result.success();
    }

    @GetMapping("/banned-words")
    @Operation(summary = "屏蔽词列表", operationId = "adminCommunityBannedWords")
    public Result<List<AdminCommunityBannedWordResponse>> bannedWords(
            @RequestParam(required = false) String keyword) {
        return Result.success(communityAdminService.bannedWords(keyword));
    }

    @PostMapping("/banned-words")
    @Operation(summary = "新增屏蔽词", description = "命中后发帖/评论自动隐藏并通知作者。",
            operationId = "adminCommunityCreateBannedWord")
    public Result<AdminCommunityBannedWordResponse> createBannedWord(
            @RequestBody @Valid AdminCommunityBannedWordRequest request) {
        return Result.success(communityAdminService.createBannedWord(request));
    }

    @PutMapping("/banned-words/{id}")
    @Operation(summary = "更新屏蔽词", operationId = "adminCommunityUpdateBannedWord")
    public Result<AdminCommunityBannedWordResponse> updateBannedWord(
            @PathVariable Long id, @RequestBody @Valid AdminCommunityBannedWordRequest request) {
        return Result.success(communityAdminService.updateBannedWord(id, request));
    }

    @DeleteMapping("/banned-words/{id}")
    @Operation(summary = "删除屏蔽词", operationId = "adminCommunityDeleteBannedWord")
    public Result<Void> deleteBannedWord(@PathVariable Long id) {
        communityAdminService.deleteBannedWord(id);
        return Result.success();
    }

    @GetMapping("/banned-words/export")
    @Operation(summary = "导出屏蔽词 Excel", description = "列：屏蔽词/备注/状态，可直接修改后重新导入。",
            operationId = "adminCommunityExportBannedWords")
    public ResponseEntity<byte[]> exportBannedWords() {
        byte[] data = communityAdminService.exportBannedWords();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=banned-words.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/banned-words/import")
    @Operation(summary = "导入屏蔽词 Excel", description = "首行为表头；重复词跳过，返回新增/重复/无效数量。",
            operationId = "adminCommunityImportBannedWords")
    public Result<AdminBannedWordImportResult> importBannedWords(@RequestPart("file") MultipartFile file) {
        return Result.success(communityAdminService.importBannedWords(file));
    }

    @GetMapping("/appeals")
    @Operation(summary = "申诉列表", description = "按状态 PENDING/APPROVED/REJECTED 与对象类型过滤。",
            operationId = "adminCommunityAppeals")
    public Result<PageResult<AdminCommunityAppealResponse>> appeals(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType) {
        return Result.success(communityAdminService.pageAppeals(pageNum, pageSize, status, targetType));
    }

    @PostMapping("/appeals/{id}/handle")
    @Operation(summary = "处理申诉", description = "APPROVE 通过并恢复内容/撤销处罚；REJECT 驳回。结果通知用户。",
            operationId = "adminCommunityHandleAppeal")
    public Result<Void> handleAppeal(@PathVariable Long id,
                                     @RequestBody @Valid AdminCommunityAppealHandleRequest request) {
        communityAdminService.handleAppeal(id, request);
        return Result.success();
    }
    @PostMapping("/posts/batch-status")
    @Operation(summary = "批量下架/恢复帖子（C02）",
            description = "action=HIDE 下架 / RESTORE 恢复；单次最多 200 条，逐条校验并返回跳过原因，作者会收到站内通知。",
            operationId = "adminBatchCommunityPostStatus")
    public Result<AdminCommunityBatchResult> batchPostStatus(
            @RequestBody @Valid AdminCommunityBatchPostStatusRequest request) {
        return Result.success(communityAdminService.batchPostStatus(request));
    }

    @PostMapping("/sections/reorder")
    @Operation(summary = "分区批量排序（C05）",
            description = "按 sectionIds 数组顺序写入 sortOrder=1..n，用于拖拽/上移下移保存。",
            operationId = "adminReorderCommunitySections")
    public Result<AdminCommunityBatchResult> reorderSections(
            @RequestBody @Valid AdminCommunitySectionReorderRequest request) {
        return Result.success(communityAdminService.reorderSections(request));
    }

    @PostMapping("/topics/{id}/recommend")
    @Operation(summary = "话题推荐位开关（C06）", operationId = "adminRecommendCommunityTopic")
    public Result<AdminCommunityTopicResponse> recommendTopic(@PathVariable Long id,
                                                              @RequestParam boolean recommended) {
        return Result.success(communityAdminService.recommendTopic(id, recommended));
    }

    @PostMapping("/topics/{id}/merge")
    @Operation(summary = "话题合并（C06）",
            description = "把源话题的帖子标签改为目标话题，源话题停用并记录 mergedIntoId，热度累加到目标话题。",
            operationId = "adminMergeCommunityTopic")
    public Result<AdminCommunityTopicResponse> mergeTopic(@PathVariable Long id,
                                                          @RequestBody @Valid AdminCommunityTopicMergeRequest request) {
        return Result.success(communityAdminService.mergeTopic(id, request.targetTopicId()));
    }

    @GetMapping("/topics/{id}/stats")
    @Operation(summary = "话题统计（C06）", description = "关联帖子总数与近 7 天新增，用于判断热度与合并影响面。",
            operationId = "adminCommunityTopicStats")
    public Result<AdminCommunityTopicStatsResponse> topicStats(@PathVariable Long id) {
        return Result.success(communityAdminService.topicStats(id));
    }

    @GetMapping("/moderation-templates")
    @Operation(summary = "处理模板列表（C03/C04）",
            description = "templateType=REPORT_NOTE 举报处理说明 / APPEAL_REPLY 申诉回复；返回平台内置（builtin=true）与当前校区自定义模板。",
            operationId = "adminListModerationTemplates")
    public Result<List<AdminModerationTemplateResponse>> templates(
            @RequestParam(required = false) String templateType) {
        return Result.success(communityAdminService.templates(templateType));
    }

    @PostMapping("/moderation-templates")
    @Operation(summary = "新建处理模板", operationId = "adminCreateModerationTemplate")
    public Result<AdminModerationTemplateResponse> createTemplate(
            @RequestBody @Valid AdminModerationTemplateRequest request) {
        return Result.success(communityAdminService.createTemplate(request));
    }

    @PutMapping("/moderation-templates/{id}")
    @Operation(summary = "更新处理模板", description = "平台内置模板不可修改。",
            operationId = "adminUpdateModerationTemplate")
    public Result<AdminModerationTemplateResponse> updateTemplate(
            @PathVariable Long id, @RequestBody @Valid AdminModerationTemplateRequest request) {
        return Result.success(communityAdminService.updateTemplate(id, request));
    }

    @DeleteMapping("/moderation-templates/{id}")
    @Operation(summary = "删除处理模板", description = "平台内置模板不可删除。",
            operationId = "adminDeleteModerationTemplate")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        communityAdminService.deleteTemplate(id);
        return Result.success();
    }

}
