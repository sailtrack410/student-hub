package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommunityPostResponse(Long postId, Long sectionId, String sectionName, String sectionIcon,
                                    Long authorId, String authorName, String avatarUrl, String title,
                                    String content, List<String> imageUrls, List<String> thumbnailUrls, List<String> topics,
                                    int commentCount, int likeCount, boolean liked, LocalDateTime createdAt) {
}
