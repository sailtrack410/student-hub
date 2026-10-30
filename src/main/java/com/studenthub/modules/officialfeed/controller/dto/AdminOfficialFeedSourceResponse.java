package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "官方通知抓取来源")
public record AdminOfficialFeedSourceResponse(
        Long id,
        String sourceName,
        String listUrl,
        Long sectionId,
        String sectionName,
        Long authorId,
        String authorName,
        String authorTitle,
        Integer maxPages,
        Integer maxArticles,
        Integer maxAgeDays,
        boolean enabled,
        LocalDateTime lastFetchAt,
        String lastStatus) {
}
