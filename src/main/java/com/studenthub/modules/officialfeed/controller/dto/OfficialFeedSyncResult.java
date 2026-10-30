package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "官方通知同步结果")
public record OfficialFeedSyncResult(
        Long sourceId,
        String sourceName,
        int discovered,
        int published,
        int skipped,
        int failed,
        String status,
        List<Item> items) {

    @Schema(description = "单篇处理结果")
    public record Item(
            String title,
            String sourceUrl,
            Long postId,
            String postType,
            String status,
            String message) {
    }
}
