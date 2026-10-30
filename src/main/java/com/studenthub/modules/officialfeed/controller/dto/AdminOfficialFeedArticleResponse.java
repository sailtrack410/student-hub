package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "官方通知抓取记录")
public record AdminOfficialFeedArticleResponse(
        Long id,
        Long sourceId,
        String sourceName,
        String title,
        String sourceUrl,
        LocalDateTime publishedAt,
        Long postId,
        String postType,
        Integer imageCount,
        String status,
        String importMode,
        boolean copyrightConfirmed,
        LocalDateTime scheduledAt,
        Integer retryCount,
        boolean hasContent,
        String errorMessage,
        LocalDateTime fetchedAt) {
}
