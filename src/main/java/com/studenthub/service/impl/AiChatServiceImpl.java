package com.studenthub.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiChatRequest;
import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.integration.ai.AiProvider;
import com.studenthub.integration.ai.AiProperties;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiProvider aiProvider;
    private final AiProperties aiProperties;

    @Override
    public AiChatResponse chat(String userMessage) {
        return chat(List.of(new AiMessage("user", userMessage)));
    }

    @Override
    public AiChatResponse chat(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息不能为空");
        }
        AiMessage last = messages.get(messages.size() - 1);
        if (!"user".equals(last.role()) || last.content() == null || last.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息不能为空");
        }
        for (AiMessage message : messages) {
            if (message.content() != null && message.content().length() > aiProperties.getMaxInputCharacters()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息长度超过限制");
            }
        }
        return aiProvider.chat(new AiChatRequest(aiProperties.getModel(), messages));
    }
}
