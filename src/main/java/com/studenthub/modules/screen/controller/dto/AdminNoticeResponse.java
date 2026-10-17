package com.studenthub.modules.screen.controller.dto;

import com.studenthub.modules.screen.entity.ScreenNoticeEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理端公告项")
public record AdminNoticeResponse(
        @Schema(description = "公告 ID") Long id,
        @Schema(description = "类型：NOTICE/NEWS/ACTIVITY") String noticeType,
        @Schema(description = "类型中文") String typeText,
        @Schema(description = "标题") String title,
        @Schema(description = "摘要", nullable = true) String summary,
        @Schema(description = "发布日期 yyyy-MM-dd", nullable = true) LocalDate publishDate,
        @Schema(description = "投放渠道：SCREEN/APP") String channel,
        @Schema(description = "发布状态：DRAFT/SCHEDULED/PUBLISHED/OFFLINE") String publishStatus,
        @Schema(description = "定时发布时间", nullable = true) LocalDateTime publishAt,
        @Schema(description = "排序值") Integer sortOrder,
        @Schema(description = "发布方署名", nullable = true) String author,
        @Schema(description = "是否启用") boolean enabled) {

    public static AdminNoticeResponse from(ScreenNoticeEntity entity, String typeText) {
        return new AdminNoticeResponse(entity.getId(), entity.getNoticeType(), typeText,
                entity.getTitle(), entity.getSummary(), entity.getPublishDate(),
                entity.getChannel(), entity.getPublishStatus(), entity.getPublishAt(),
                entity.getSortOrder(), entity.getAuthor(), Boolean.TRUE.equals(entity.getEnabled()));
    }
}
