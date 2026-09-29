package com.studenthub.modules.message.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 统一收件箱条目（前端需求清单 5.2）：三路会话源（系统通知/私信/AI 助手）
 * 折叠为同一扁平流，lastTime 为原始时间戳，前端自行做相对时间展示。
 */
@Schema(description = "统一收件箱会话条目")
public record InboxItemResponse(
        @Schema(description = "会话标识：PROCESS_NOTIFICATION / DIRECT_MESSAGE:<peerId> / AI_ASSISTANT:<conversationId>") String sessionId,
        @Schema(description = "会话类型 PROCESS_NOTIFICATION/DIRECT_MESSAGE/AI_ASSISTANT") String sessionType,
        @Schema(description = "私信对端用户 ID，仅 DIRECT_MESSAGE 返回", nullable = true) Long peerId,
        @Schema(description = "AI 会话 ID，仅 AI_ASSISTANT 返回", nullable = true) Long conversationId,
        @Schema(description = "会话标题") String title,
        @Schema(description = "头像地址，固定类型为内置图标，私信为用户头像", nullable = true) String avatarUrl,
        @Schema(description = "最后一条消息内容") String lastContent,
        @Schema(description = "最后消息时间") LocalDateTime lastTime,
        @Schema(description = "未读数") long unreadCount,
        @Schema(description = "是否置顶（当前固定：流程通知置顶）") boolean pinned) {
}
