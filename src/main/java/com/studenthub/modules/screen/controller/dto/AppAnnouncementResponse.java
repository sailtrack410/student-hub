package com.studenthub.modules.screen.controller.dto;

import com.studenthub.modules.screen.entity.ScreenNoticeEntity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * App 校园公告条目：需求清单 4.2 字段；来源为 APP 渠道已发布公告。
 */
@Schema(description = "App 校园公告条目")
public record AppAnnouncementResponse(
        @Schema(description = "公告 id") Long id,
        @Schema(description = "标题") String title,
        @Schema(description = "类型：NOTICE/NEWS/ACTIVITY") String type,
        @Schema(description = "类型中文：通知/要闻/活动") String typeText,
        @Schema(description = "摘要") String summary,
        @Schema(description = "发布日期 yyyy-MM-dd") String publishDate,
        @Schema(description = "发布方署名，如 保卫处") String author) {

    public static AppAnnouncementResponse from(ScreenNoticeEntity entity, String typeText) {
        return new AppAnnouncementResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getNoticeType(),
                typeText,
                entity.getSummary(),
                entity.getPublishDate() == null ? null : entity.getPublishDate().toString(),
                entity.getAuthor());
    }
}
