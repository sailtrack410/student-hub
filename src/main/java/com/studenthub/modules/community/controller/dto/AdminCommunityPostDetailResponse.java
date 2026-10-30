package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端帖子详情：完整正文块 + 图片签名地址 + 治理信息 */
@Schema(description = "管理端帖子详情")
public record AdminCommunityPostDetailResponse(
        Long postId,
        Long sectionId,
        String sectionName,
        String postType,
        String title,
        String content,
        String status,
        String hiddenReason,
        LocalDateTime hiddenAt,
        int reportCount,
        int commentCount,
        int likeCount,
        Long authorId,
        String authorName,
        List<String> topics,
        List<CommunityContentBlockResponse> contentBlocks,
        List<String> imageUrls,
        List<String> thumbnailUrls,
        LocalDateTime createdAt) {
}
