package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;

public record CommunityCommentResponse(Long commentId, Long parentCommentId, Long authorId, String authorName,
                                       String avatarUrl, String content, java.util.List<String> imageUrls, java.util.List<String> thumbnailUrls, int likeCount, boolean liked,
                                       boolean pinned, boolean author, LocalDateTime createdAt) {
}
