package com.studenthub.modules.community.controller.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;

import java.time.LocalDateTime;
import java.util.List;

public record DirectMessageResponse(Long messageId, Long senderId, Long receiverId,
                                    String senderAvatarUrl, String content, List<String> imageUrls, List<String> thumbnailUrls,
                                    DirectMessageReferenceCard referenceCard,
                                    LocalDateTime createdAt,
                                    LocalDateTime deliveredAt,
                                    LocalDateTime readAt,
                                    String status) {

    private static final ObjectMapper CARD_MAPPER = new ObjectMapper();

    public static DirectMessageResponse from(CommunityDirectMessageEntity entity, String senderAvatarUrl,
                                             List<String> imageUrls, List<String> thumbnailUrls) {
        return from(entity, senderAvatarUrl, imageUrls, thumbnailUrls, parseCard(entity));
    }

    public static DirectMessageResponse from(CommunityDirectMessageEntity entity, String senderAvatarUrl,
                                             List<String> imageUrls, List<String> thumbnailUrls,
                                             DirectMessageReferenceCard referenceCard) {
        String status = entity.getReadAt() != null ? "READ"
                : entity.getDeliveredAt() != null ? "DELIVERED" : "SENT";
        return new DirectMessageResponse(entity.getId(), entity.getSenderId(), entity.getReceiverId(),
                senderAvatarUrl, entity.getContent(), imageUrls, thumbnailUrls, referenceCard, entity.getCreatedAt(),
                entity.getDeliveredAt(), entity.getReadAt(), status);
    }

    /** 从落库卡片快照 JSON 还原；解析失败按无卡片处理（不阻断消息流） */
    public static DirectMessageReferenceCard parseCard(CommunityDirectMessageEntity entity) {
        if (entity.getReferenceCard() == null || entity.getReferenceCard().isBlank()) {
            return null;
        }
        try {
            return CARD_MAPPER.readValue(entity.getReferenceCard(), DirectMessageReferenceCard.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
