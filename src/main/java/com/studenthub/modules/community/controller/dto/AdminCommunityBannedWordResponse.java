package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 管理端屏蔽词响应（含匹配方式与命中统计） */
@Schema(description = "屏蔽词（管理端）")
public record AdminCommunityBannedWordResponse(
        Long id,
        String word,
        String remark,
        String status,
        String matchType,
        @Schema(description = "累计命中次数") Integer hitCount,
        @Schema(description = "最近命中时间") LocalDateTime lastHitAt,
        LocalDateTime createdAt) {
}
