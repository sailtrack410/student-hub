package com.studenthub.integration.ai;

import java.util.List;

/**
 * 聊天请求。vision 为 true 时走图片识别默认模型并发送多模态消息。
 */
public record AiChatRequest(String model, List<AiMessage> messages, boolean vision) {

    public AiChatRequest(String model, List<AiMessage> messages) {
        this(model, messages, false);
    }
}