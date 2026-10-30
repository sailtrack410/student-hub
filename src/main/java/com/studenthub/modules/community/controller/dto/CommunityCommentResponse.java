package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;

/**
 * 评论响应：weight 为热度权重（点赞 +1、举报 -3），客户端「热门」排序使用。
 */
public record CommunityCommentResponse(Long commentId, Long parentCommentId, Long authorId, String authorName,
                                       String avatarUrl, boolean authorOfficial, String authorOfficialTitle,
                                       String content, java.util.List<String> imageUrls, java.util.List<String> thumbnailUrls, int likeCount, int weight, boolean liked,
                                       boolean pinned, boolean author, LocalDateTime createdAt) {
}
