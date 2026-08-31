package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 现场终端信息要闻：仅公开通知/要闻/活动，供待机屏与主控台轮播展示。
 */
@Schema(description = "现场终端信息要闻")
public record ScreenNoticeResponse(
        @Schema(description = "要闻列表") List<Notice> notices) {

    public record Notice(
            @Schema(description = "类型：NOTICE/NEWS/ACTIVITY") String type,
            @Schema(description = "类型中文：通知/要闻/活动") String typeText,
            @Schema(description = "标题") String title,
            @Schema(description = "摘要") String summary,
            @Schema(description = "发布日期 yyyy-MM-dd") String publishDate) {
    }
}
