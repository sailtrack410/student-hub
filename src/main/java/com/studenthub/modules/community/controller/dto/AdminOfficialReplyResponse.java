package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 官方回复结果（C12）。 */
@Schema(description = "官方回复结果")
public record AdminOfficialReplyResponse(
        @Schema(description = "评论 ID") Long commentId,
        @Schema(description = "帖子 ID") Long postId,
        @Schema(description = "官方账号 ID") Long officialUserId,
        @Schema(description = "官方账号展示名") String officialName,
        @Schema(description = "回复内容") String content,
        @Schema(description = "发布时间") LocalDateTime createdAt) {
}
