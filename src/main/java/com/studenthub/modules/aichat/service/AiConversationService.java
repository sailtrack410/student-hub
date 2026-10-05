package com.studenthub.modules.aichat.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.aichat.controller.dto.AiConversationResponse;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import com.studenthub.modules.identity.security.AuthenticatedUser;

import java.util.List;

public interface AiConversationService {

    AiConversationResponse createConversation(Long userId, String title);

    PageResult<AiConversationResponse> pageMyConversations(Long userId, Integer pageNum, Integer pageSize);

    List<AiMessageResponse> listMessages(Long userId, Long conversationId);

    /**
     * 追加用户消息并返回助手回复；AI 不可用时返回降级回复，不抛出异常。
     * 会按 intent 查询当前用户本人的课表/申请/服务入口数据并注入回复。
     */
    AiMessageResponse sendMessage(AuthenticatedUser user, Long conversationId, SendMessageRequest request);

    /**
     * App SSE 流式对话（API需求清单 6.1）：校验/用户消息落库/意图数据与 sendMessage 同链路，
     * 助手回复按增量推给 onDelta，结束后完整回复落库并经 onDone 返回最终消息。
     * 调用方负责 SseEmitter 生命周期；本方法在工作线程执行。
     */
    void sendMessageStream(AuthenticatedUser user, Long conversationId, SendMessageRequest request,
                           java.util.function.Consumer<String> onDelta,
                           java.util.function.Consumer<AiMessageResponse> onDone,
                           java.util.function.Consumer<String> onError);

    /**
     * 上传的音频文件转写为文本，供发送消息使用；未配置 ASR 模型时抛业务异常。
     */
    String transcribeAudio(Long fileId);
}