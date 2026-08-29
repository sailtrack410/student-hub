package com.studenthub.modules.community.controller.dto;

import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;

import java.time.LocalDateTime;
import java.util.List;

public record DirectMessageResponse(Long messageId, Long senderId, Long receiverId,
                                    String senderAvatarUrl, String content, List<String> imageUrls, List<String> thumbnailUrls,
                                    LocalDateTime createdAt) {
    public static DirectMessageResponse from(CommunityDirectMessageEntity entity, String senderAvatarUrl,
                                             List<String> imageUrls, List<String> thumbnailUrls) {
        return new DirectMessageResponse(entity.getId(), entity.getSenderId(), entity.getReceiverId(),
                senderAvatarUrl, entity.getContent(), imageUrls, thumbnailUrls, entity.getCreatedAt());
    }
}
