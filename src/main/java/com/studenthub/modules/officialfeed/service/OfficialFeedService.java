package com.studenthub.modules.officialfeed.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedArticleResponse;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedArticleUpdateRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedImportRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedPreviewResponse;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedSourceRequest;
import com.studenthub.modules.officialfeed.controller.dto.AdminOfficialFeedSourceResponse;
import com.studenthub.modules.officialfeed.controller.dto.OfficialFeedSyncResult;
import com.studenthub.modules.officialfeed.controller.dto.OfficialUserResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface OfficialFeedService {

    List<AdminOfficialFeedSourceResponse> listSources();

    List<OfficialUserResponse> officialUsers();

    AdminOfficialFeedSourceResponse createSource(AdminOfficialFeedSourceRequest request);

    AdminOfficialFeedSourceResponse updateSource(Long sourceId, AdminOfficialFeedSourceRequest request);

    OfficialFeedSyncResult sync(Long sourceId);

    List<OfficialFeedSyncResult> syncAllEnabled();

    PageResult<AdminOfficialFeedArticleResponse> pageArticles(int pageNum, int pageSize, Long sourceId, String status);

    // ----- C08 单篇 URL 导入 / 预览 / 编辑 / 定时 / 重试 / 版权确认 -----

    /** 单篇 URL 导入：抓取正文与图片后落草稿，返回预览（幂等：已存在非失败记录时直接返回预览） */
    AdminOfficialFeedPreviewResponse importByUrl(AdminOfficialFeedImportRequest request);

    AdminOfficialFeedPreviewResponse previewArticle(Long articleId);

    AdminOfficialFeedPreviewResponse updateArticle(Long articleId, AdminOfficialFeedArticleUpdateRequest request);

    AdminOfficialFeedPreviewResponse confirmCopyright(Long articleId, boolean confirmed);

    /** 立即发布（要求版权已确认、正文非空；重复发布返回 400） */
    AdminOfficialFeedPreviewResponse publishArticle(Long articleId);

    AdminOfficialFeedPreviewResponse scheduleArticle(Long articleId, LocalDateTime scheduledAt);

    /** 失败重试：重新抓取原文并重建图文块，失败时保留 FAILED 状态与错误信息（不伪造成功） */
    AdminOfficialFeedPreviewResponse retryArticle(Long articleId);

    /** 定时任务扫描：把到点的 SCHEDULED 文章发布，返回本轮发布数 */
    int publishDueScheduledArticles();
}
