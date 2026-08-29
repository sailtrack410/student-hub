package com.studenthub.modules.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.modules.aichat.config.AiChatProperties;
import com.studenthub.modules.aichat.controller.dto.AiConversationResponse;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.modules.aichat.service.AiConversationService;
import com.studenthub.modules.aichat.service.CampusAssistantPrompts;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl implements AiConversationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String MESSAGE_STATUS_OK = "OK";
    private static final String MESSAGE_STATUS_FAILED = "FAILED";

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiChatService aiChatService;
    private final AiChatProperties aiChatProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationResponse createConversation(Long userId, String title) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setUserId(userId);
        entity.setTitle(title == null || title.isBlank() ? "新对话" : title.trim());
        entity.setStatus(STATUS_ACTIVE);
        conversationMapper.insert(entity);
        return AiConversationResponse.from(entity);
    }

    @Override
    public PageResult<AiConversationResponse> pageMyConversations(Long userId, Integer pageNum, Integer pageSize) {
        Page<AiConversationEntity> page = conversationMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize),
                new LambdaQueryWrapper<AiConversationEntity>()
                        .eq(AiConversationEntity::getUserId, userId)
                        .orderByDesc(AiConversationEntity::getUpdatedAt));
        return PageResult.of(page.convert(AiConversationResponse::from));
    }

    @Override
    public List<AiMessageResponse> listMessages(Long userId, Long conversationId) {
        requireOwnedConversation(userId, conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                        .eq(AiMessageEntity::getConversationId, conversationId)
                        .orderByAsc(AiMessageEntity::getCreatedAt))
                .stream().map(AiMessageResponse::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        AiConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        String content = request.content().trim();
        if (content.length() > currentMaxMessageCharacters()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "消息长度超过限制");
        }
        long sentLastHour = messageMapper.countUserMessagesSince(userId, LocalDateTime.now().minusHours(1));
        if (sentLastHour >= currentHourlyMessageLimit()) {
            log.info("AI 消息触发限流, userId={}", userId);
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }

        AiMessageEntity userMessage = new AiMessageEntity();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(content);
        userMessage.setStatus(MESSAGE_STATUS_OK);
        messageMapper.insert(userMessage);

        long start = System.currentTimeMillis();
        String reply;
        boolean degraded = false;
        String errorCode = null;
        String model = null;
        Integer tokenUsage = null;
        try {
            List<AiMessage> prompt = buildPrompt(content, conversation.getId());
            AiChatResponse response = aiChatService.chat(prompt);
            reply = response.content();
            model = response.model();
            Integer promptTokens = response.promptTokens();
            Integer completionTokens = response.completionTokens();
            tokenUsage = promptTokens == null && completionTokens == null
                    ? null : (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
        } catch (BusinessException exception) {
            log.warn("AI 调用失败, conversationId={}, code={}", conversation.getId(), exception.getCode());
            reply = CampusAssistantPrompts.DEGRADED_REPLY;
            degraded = true;
            errorCode = exception.getCode();
        }

        AiMessageEntity assistantMessage = new AiMessageEntity();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(reply);
        assistantMessage.setModel(model);
        assistantMessage.setPromptVersion(CampusAssistantPrompts.PROMPT_VERSION);
        assistantMessage.setTokenUsage(tokenUsage);
        assistantMessage.setCostMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));
        assistantMessage.setErrorCode(errorCode);
        assistantMessage.setStatus(degraded ? MESSAGE_STATUS_FAILED : MESSAGE_STATUS_OK);
        messageMapper.insert(assistantMessage);

        touchConversation(conversation);
        return AiMessageResponse.from(assistantMessage);
    }

    private List<AiMessage> buildPrompt(String currentContent, Long conversationId) {
        List<AiMessage> prompt = new ArrayList<>();
        prompt.add(new AiMessage("system", CampusAssistantPrompts.SYSTEM_PROMPT));
        List<AiMessageEntity> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversationId)
                .orderByDesc(AiMessageEntity::getCreatedAt)
                .last("LIMIT " + currentHistoryWindow()));
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessageEntity entity = history.get(i);
            if (MESSAGE_STATUS_OK.equals(entity.getStatus())) {
                prompt.add(new AiMessage(entity.getRole().toLowerCase(), entity.getContent()));
            }
        }
        prompt.add(new AiMessage("user", currentContent));
        return prompt;
    }

    /** 会话/限流参数：优先读运行时快照（管理后台动态配置），无快照回退静态配置 */
    private int currentHourlyMessageLimit() {
        var snapshot = AiRuntimeConfig.current();
        return snapshot != null ? snapshot.hourlyMessageLimit() : aiChatProperties.getHourlyMessageLimit();
    }

    private int currentHistoryWindow() {
        var snapshot = AiRuntimeConfig.current();
        return snapshot != null ? snapshot.historyWindow() : aiChatProperties.getHistoryWindow();
    }

    private int currentMaxMessageCharacters() {
        var snapshot = AiRuntimeConfig.current();
        return snapshot != null ? snapshot.maxMessageCharacters() : aiChatProperties.getMaxMessageCharacters();
    }

    private void touchConversation(AiConversationEntity conversation) {
        AiConversationEntity update = new AiConversationEntity();
        update.setId(conversation.getId());
        update.setVersion(conversation.getVersion());
        conversationMapper.updateById(update);
    }

    private AiConversationEntity requireOwnedConversation(Long userId, Long conversationId) {
        AiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }
}
