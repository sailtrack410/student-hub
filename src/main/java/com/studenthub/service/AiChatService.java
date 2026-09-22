package com.studenthub.service;

import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;

import java.util.List;
import java.util.function.Consumer;

public interface AiChatService {

    AiChatResponse chat(String userMessage);

    /**
     * 多轮对话：messages 含历史上下文，最后一条为当前用户消息。
     */
    AiChatResponse chat(List<AiMessage> messages);

    /**
     * 多模态对话：路由到图片识别默认模型（VISION），最后一条用户消息可携带图片。
     */
    AiChatResponse chatVision(List<AiMessage> messages);

    /**
     * 流式对话：按默认聊天模型的协议与 streaming 开关调用供应商。
     * 增量文本经 onDelta 推送；结束时 onDone(model, promptTokens, completionTokens)。
     * 供应商关闭流式时回退为一次性回调。
     */
    void chatStream(List<AiMessage> messages, Consumer<String> onDelta,
                    Consumer<StreamDone> onDone);

    /** 流式结束信息 */
    record StreamDone(String model, Integer promptTokens, Integer completionTokens) {
    }
}
