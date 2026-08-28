package com.studenthub.modules.aichat.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.aichat.controller.dto.AiConversationResponse;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;

import java.util.List;

public interface AiConversationService {

    AiConversationResponse createConversation(Long userId, String title);

    PageResult<AiConversationResponse> pageMyConversations(Long userId, Integer pageNum, Integer pageSize);

    List<AiMessageResponse> listMessages(Long userId, Long conversationId);

    /**
     * 追加用户消息并返回助手回复；AI 不可用时返回降级回复，不抛出异常。
     */
    AiMessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request);
}
