package com.studenthub.modules.community.service;

import com.studenthub.modules.community.controller.dto.DirectMessageResponse;
import com.studenthub.modules.community.controller.dto.MessageReceiptResponse;
import com.studenthub.modules.community.controller.dto.SendDirectMessageRequest;
import com.studenthub.modules.identity.security.AuthenticatedUser;

import java.util.List;

public interface DirectMessageService {
    List<DirectMessageResponse> conversation(AuthenticatedUser user, Long withUserId);
    DirectMessageResponse send(AuthenticatedUser user, SendDirectMessageRequest request);
    List<com.studenthub.modules.community.controller.dto.DirectConversationResponse> contacts(AuthenticatedUser user);
    long unreadCount(AuthenticatedUser user);
    void markConversationRead(AuthenticatedUser user, Long withUserId);
    MessageReceiptResponse markDelivered(AuthenticatedUser user, List<Long> messageIds);
    MessageReceiptResponse markRead(AuthenticatedUser user, Long withUserId, List<Long> messageIds);
    boolean canContact(AuthenticatedUser user, Long peerId);
    boolean isOnline(Long userId);
    String lastSeenAt(Long userId);
}
