package com.studenthub.service;

import com.studenthub.integration.ai.AiChatResponse;

public interface AiChatService {

    AiChatResponse chat(String userMessage);
}
