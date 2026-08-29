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
     * 上传的音频文件转写为文本，供发送消息使用；未配置 ASR 模型时抛业务异常。
     */
    String transcribeAudio(Long fileId);
}