package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 管理端帖子列表项（带封面图与治理字段） */
@Schema(description = "管理端帖子列表项")
public record AdminCommunityPostResponse(
        Long postId,
        Long sectionId,
        String sectionName,
        String postType,
        String title,
        String summary,
        String coverUrl,
        Long authorId,
        String authorName,
        String status,
        String hiddenReason,
        int reportCount,
        int commentCount,
        int likeCount,
        LocalDateTime createdAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "是否置顶（C12）") boolean pinned) {
}
