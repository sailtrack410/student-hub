package com.studenthub.integration.ai;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        requireValidMessages(messages);
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        validateInputSize(messages, snapshot);
        AiModelBinding chat = snapshot != null ? snapshot.chat() : null;
        String model = chat != null ? chat.model() : aiProperties.getModel();
        return aiProvider.chat(new AiChatRequest(model, messages));
    }

    @Override
    public AiChatResponse chatVision(List<AiMessage> messages) {
        requireValidMessages(messages);
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        if (snapshot == null || snapshot.vision() == null) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "图片识别模型尚未配置");
        }
        validateInputSize(messages, snapshot);
        return aiProvider.chat(new AiChatRequest(snapshot.vision().model(), messages, true));
    }

    private void requireValidMessages(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息不能为空");
        }
        AiMessage last = messages.get(messages.size() - 1);
        if (!"user".equals(last.role())
                || (!StringUtils.hasText(last.content()) && !last.hasImages())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息不能为空");
        }
    }

    private void validateInputSize(List<AiMessage> messages, AiRuntimeSnapshot snapshot) {
        int maxInput = snapshot != null ? snapshot.maxInputCharacters() : aiProperties.getMaxInputCharacters();
        for (AiMessage message : messages) {
            if (message.content() != null && message.content().length() > maxInput) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户消息长度超过限制");
            }
        }
    }
}