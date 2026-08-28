package com.studenthub.service;

import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;

import java.util.List;

public interface AiChatService {

    AiChatResponse chat(String userMessage);

    /**
     * 多轮对话：messages 含历史上下文，最后一条为当前用户消息。
     */
    AiChatResponse chat(List<AiMessage> messages);
}
