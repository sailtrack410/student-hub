package com.studenthub.integration.ai;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 对话服务：每次调用读取 {@link AiRuntimeConfig} 当前快照，
 * 管理后台修改配置（模型/限流/开关）后即时生效。
 */
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
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        int maxInput = snapshot != null ? snapshot.maxInputCharacters() : aiProperties.getMaxInputCharacters();
        for (AiMessage message : messages) {
            if (message.content() != null && message.content().length() > maxInput) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息长度超过限制");
            }
        }
        AiModelBinding chat = snapshot != null ? snapshot.chat() : null;
        String model = chat != null ? chat.model() : aiProperties.getModel();
        return aiProvider.chat(new AiChatRequest(model, messages));
    }
}