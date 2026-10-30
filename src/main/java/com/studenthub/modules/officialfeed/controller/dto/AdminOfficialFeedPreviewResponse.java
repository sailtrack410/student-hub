package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 单篇导入/预览响应（C08）：含图文块签名地址、正文字数、版权与定时状态。 */
@Schema(description = "官方通知草稿预览")
public record AdminOfficialFeedPreviewResponse(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "来源 ID") Long sourceId,
        @Schema(description = "来源名称") String sourceName,
        @Schema(description = "标题") String title,
        @Schema(description = "原文 URL") String sourceUrl,
        @Schema(description = "发布时间") LocalDateTime publishedAt,
        @Schema(description = "图文块（图片已签名）") List<com.studenthub.modules.community.controller.dto.CommunityContentBlockResponse> blocks,
        @Schema(description = "纯文本正文") String plainText,
        @Schema(description = "正文字数") int wordCount,
        @Schema(description = "封面地址") String coverUrl,
        @Schema(description = "图片数") Integer imageCount,
        @Schema(description = "导入方式：CRAWL/MANUAL_URL") String importMode,
        @Schema(description = "状态：DRAFT/SCHEDULED/PUBLISHED/FAILED/SKIPPED") String status,
        @Schema(description = "版权是否已确认") boolean copyrightConfirmed,
        @Schema(description = "定时发布时间") LocalDateTime scheduledAt,
        @Schema(description = "重试次数") Integer retryCount,
        @Schema(description = "失败原因") String errorMessage,
        @Schema(description = "已生成帖子 ID") Long postId) {
}
