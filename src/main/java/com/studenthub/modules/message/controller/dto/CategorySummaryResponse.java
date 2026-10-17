package com.studenthub.modules.message.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 消息中心四大分类未读聚合（需求清单 5.1）。
 */
@Schema(description = "消息中心分类未读汇总")
public record CategorySummaryResponse(
        @Schema(description = "流程办理通知未读数（非社区类站内通知）") long processNoticeUnread,
        @Schema(description = "互动消息未读数（社区通知 + 私信）") long interactionUnread,
        @Schema(description = "公告未读数（公告暂无已读态，恒为 0）") long announcementUnread,
        @Schema(description = "AI 待办数（待办功能未上线，恒为 0）") long aiTodoCount,
        @Schema(description = "未读总数") long totalUnread) {
}
