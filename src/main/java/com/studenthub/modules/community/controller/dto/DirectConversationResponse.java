package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;

public record DirectConversationResponse(Long peerId, String peerName, String avatarUrl,
                                         String lastContent, LocalDateTime lastAt,
                                         boolean online, long unreadCount) {
}
