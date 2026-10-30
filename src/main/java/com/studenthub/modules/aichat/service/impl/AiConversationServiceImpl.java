package com.studenthub.modules.aichat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.aichat.controller.dto.AiActionResponse;
import com.studenthub.modules.aichat.controller.dto.AiSourceResponse;
import com.studenthub.modules.aichat.intent.AiAssistantDataService;
import com.studenthub.modules.aichat.intent.AiAssistantResolved;
import com.studenthub.modules.aichat.intent.AiIntent;
import com.studenthub.modules.aichat.intent.AiIntentClassifier;
import com.studenthub.modules.aichat.intent.AiIntentMatch;
import com.studenthub.modules.identity.security.AuthenticatedUser;
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
import com.studenthub.modules.aichat.service.AiSpeechService;
import com.studenthub.modules.aichat.service.CampusAssistantPrompts;
import com.studenthub.modules.aichat.service.context.AiContextAssembler;
import com.studenthub.modules.aichat.service.context.ConversationSummaryService;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl implements AiConversationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String MESSAGE_STATUS_OK = "OK";
    private static final String MESSAGE_STATUS_FAILED = "FAILED";
    /** 单份文档注入提示词的字符上限，超出截断 */
    private static final int MAX_DOCUMENT_CHARACTERS = 8000;

    private final com.studenthub.modules.aichat.service.execution.AiRequestExecutionService requestExecution;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final com.studenthub.modules.rag.service.KnowledgeDocumentParser documentParser;
    private final AiChatService aiChatService;
    private final AiSpeechService aiSpeechService;
    private final AiChatProperties aiChatProperties;
    private final AiIntentClassifier intentClassifier;
    private final AiAssistantDataService assistantDataService;
    private final FileStorageService fileStorageService;
    private final ObjectProvider<com.studenthub.modules.rag.service.KnowledgeBaseService> knowledgeBaseService;
    private final AiContextAssembler contextAssembler;
    private final ConversationSummaryService conversationSummaryService;
    private final ObjectMapper objectMapper;
    private final com.studenthub.modules.aichat.security.AiSecurityGuard securityGuard;

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
    public AiMessageResponse sendMessage(AuthenticatedUser user, Long conversationId, SendMessageRequest request) {
        requireOwnedConversation(user.userId(), conversationId);
        return requestExecution.execute(user.userId(), conversationId, request,
                () -> sendMessageOnce(user, conversationId, request));
    }

    private AiMessageResponse sendMessageOnce(AuthenticatedUser user, Long conversationId, SendMessageRequest request) {
        Long userId = user.userId();
        AiConversationEntity conversation = requireOwnedConversation(userId, conversationId);
        String content = request.content() == null ? "" : request.content().trim();
        List<Long> imageIds = normalizeImageIds(request.effectiveImageIds());
        List<Long> documentIds = normalizeDocumentIds(request.effectiveDocumentIds());
        if (content.isEmpty() && imageIds.isEmpty() && documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "消息不能为空");
        }
        if (content.length() > currentMaxMessageCharacters()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "消息长度超过限制");
        }
        // I04 安全策略：敏感词拦截 + 分钟级限流 + 单用户每日配额（策略关闭时全部放行）
        securityGuard.checkUserMessage(userId, content);
        long sentLastHour = messageMapper.countUserMessagesSince(userId, LocalDateTime.now().minusHours(1));
        if (sentLastHour >= currentHourlyMessageLimit()) {
            log.info("AI 消息触发限流, userId={}", userId);
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }
        // 图片走多模态识别，读取文件内容构造 OpenAI 图片 data URL
        List<String> imageDataUrls = imageIds.stream().map(this::toImageDataUrl).toList();
        // 文档走服务端读取注入提示词（当前用户已上传文件，防越权）
        String documentText = documentIds.isEmpty() ? null : readDocumentText(documentIds);

        AiMessageEntity userMessage = new AiMessageEntity();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(content);
        userMessage.setImages(serializeImageIds(imageIds));
        userMessage.setStatus(MESSAGE_STATUS_OK);
        messageMapper.insert(userMessage);

        AiIntentMatch intent = imageIds.isEmpty()
                ? intentClassifier.classify(content)
                : AiIntentMatch.fallback();        AiAssistantResolved resolved = resolveForIntent(intent, user);

        long start = System.currentTimeMillis();
        String reply = null;
        String model = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer tokenUsage = null;
        String errorCode = null;
        String actionsJson = assistantDataService.serializeActions(resolved == null ? null : resolved.actions());
        String directTimeReply = currentTimeReply(content);
        AiContextAssembler.Bundle bundle = null;
        List<AiSourceResponse> sources = List.of();
        if (directTimeReply != null) {
            reply = directTimeReply;
            model = "system";
        } else {
            bundle = contextAssembler.assemble(conversation, content,
                    resolved == null ? null : resolved.contextText(), documentText, imageDataUrls,
                    userMessage.getId(), false);
            sources = toSources(bundle.sources());
            BusinessException failure = null;
            boolean success = false;
            try {
                ChatCall call = callChatOnce(bundle.messages(), !imageIds.isEmpty());
                reply = call.reply();
                model = call.model();
                promptTokens = call.promptTokens();
                completionTokens = call.completionTokens();
                success = true;
            } catch (BusinessException exception) {
                com.studenthub.integration.ai.AiCallCancellation.checkCurrent();
                failure = exception;
            }
            // 供应商报上下文超限：压缩历史/知识库预算后重试一次
            if (!success && imageIds.isEmpty() && isContextLength(failure)) {
                log.warn("上下文超限，压缩后重试, conversationId={}, estimated={}",
                        conversation.getId(), bundle.estimatedTokens());
                bundle = contextAssembler.assemble(conversation, content,
                        resolved == null ? null : resolved.contextText(), documentText, imageDataUrls,
                        userMessage.getId(), true);
                sources = toSources(bundle.sources());
                try {
                    ChatCall call = callChatOnce(bundle.messages(), false);
                    reply = call.reply();
                    model = call.model();
                    promptTokens = call.promptTokens();
                    completionTokens = call.completionTokens();
                    success = true;
                } catch (BusinessException retryException) {
                    com.studenthub.integration.ai.AiCallCancellation.checkCurrent();
                    failure = retryException;
                }
            }
            if (!success) {
                BusinessException exception = failure;
                log.warn("AI 调用失败, conversationId={}, code={}",
                        conversation.getId(), exception == null ? null : exception.getCode());
                // 查到确定性数据/入口时优先给本地兜底回复；否则退回统一降级文案
                if (resolved != null && resolved.actions() != null && !resolved.actions().isEmpty()) {
                    reply = localFallbackReply(intent, resolved);
                } else {
                    reply = CampusAssistantPrompts.DEGRADED_REPLY;
                    errorCode = exception == null ? ErrorCode.AI_PROVIDER_ERROR.getCode() : exception.getCode();
                }
            }
            tokenUsage = sumTokens(promptTokens, completionTokens);
        }

        AiMessageEntity assistantMessage = new AiMessageEntity();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(reply);
        assistantMessage.setActions(actionsJson);
        assistantMessage.setModel(model);
        assistantMessage.setPromptVersion(com.studenthub.modules.aichat.security.AiPromptRuntime.promptVersion());
        assistantMessage.setTokenUsage(tokenUsage);
        assistantMessage.setPromptTokens(promptTokens);
        assistantMessage.setCompletionTokens(completionTokens);
        assistantMessage.setSources(serializeSources(sources));
        assistantMessage.setCostMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));
        assistantMessage.setErrorCode(errorCode);
        assistantMessage.setStatus(errorCode == null ? MESSAGE_STATUS_OK : MESSAGE_STATUS_FAILED);
        messageMapper.insert(assistantMessage);

        recordContextUsage(conversation, promptTokens, bundle == null ? null : bundle.estimatedTokens());
        if (bundle != null && bundle.summaryUpToMessageId() != null) {
            conversationSummaryService.summarizeAsync(conversation.getId(), bundle.summaryUpToMessageId());
        }
        return AiMessageResponse.from(assistantMessage);
    }

    @Override
    public void sendMessageStream(AuthenticatedUser user, Long conversationId, SendMessageRequest request,
                                  java.util.function.Consumer<String> onDelta,
                                  java.util.function.Consumer<AiMessageResponse> onDone,
                                  java.util.function.Consumer<com.studenthub.modules.aichat.controller.dto.AiStreamError> onError) {
        AiMessageResponse response;
        try {
            requireOwnedConversation(user.userId(), conversationId);
            response = requestExecution.execute(user.userId(), conversationId, request,
                    () -> sendMessageStreamOnce(user, conversationId, request, onDelta));
        } catch (java.util.concurrent.CancellationException exception) {
            throw exception;
        } catch (BusinessException exception) {
            onError.accept(new com.studenthub.modules.aichat.controller.dto.AiStreamError(
                    exception.getCode(), exception.getMessage()));
            return;
        } catch (RuntimeException exception) {
            log.warn("AI 流式对话异常, conversationId={}", conversationId, exception);
            onError.accept(new com.studenthub.modules.aichat.controller.dto.AiStreamError(
                    ErrorCode.AI_PROVIDER_ERROR.getCode(), "AI 调用失败，请发起新问题"));
            return;
        }
        // execute 已完成消息及幂等结果的事务提交；此后客户端才接收 done。
        onDone.accept(response);
    }

    private AiMessageResponse sendMessageStreamOnce(AuthenticatedUser user, Long conversationId,
                                                  SendMessageRequest request,
                                                  java.util.function.Consumer<String> onDelta) {
        long start = System.currentTimeMillis();
            // 校验与用户消息落库复用非流式链路（先走一遍快速校验，避免流式开始后才发现非法）
            AiConversationEntity conversation = requireOwnedConversation(user.userId(), conversationId);
            String content = request.content() == null ? "" : request.content().trim();
            List<Long> imageIds = normalizeImageIds(request.effectiveImageIds());
            List<Long> documentIds = normalizeDocumentIds(request.effectiveDocumentIds());
            if (content.isEmpty() && imageIds.isEmpty() && documentIds.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "消息不能为空");
            }
            if (content.length() > currentMaxMessageCharacters()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "消息长度超过限制");
            }
            // I04 安全策略：与同步链路保持一致（策略关闭时全部放行）
            securityGuard.checkUserMessage(user.userId(), content);
            long sentLastHour = messageMapper.countUserMessagesSince(user.userId(), LocalDateTime.now().minusHours(1));
            if (sentLastHour >= currentHourlyMessageLimit()) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
            }
            List<String> imageDataUrls = imageIds.stream().map(this::toImageDataUrl).toList();
            String documentText = documentIds.isEmpty() ? null : readDocumentText(documentIds);

            AiMessageEntity userMessage = new AiMessageEntity();
            userMessage.setConversationId(conversation.getId());
            userMessage.setRole(ROLE_USER);
            userMessage.setContent(content);
            userMessage.setImages(serializeImageIds(imageIds));
            userMessage.setStatus(MESSAGE_STATUS_OK);
            messageMapper.insert(userMessage);

            AiIntentMatch intent = imageIds.isEmpty() ? intentClassifier.classify(content) : AiIntentMatch.fallback();
            AiAssistantResolved resolved = resolveForIntent(intent, user);
            String actionsJson = assistantDataService.serializeActions(resolved == null ? null : resolved.actions());

            String reply = null;
            String model = null;
            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer tokenUsage = null;
            String errorCode = null;
            String directTimeReply = currentTimeReply(content);
            AiContextAssembler.Bundle bundle = null;
            List<AiSourceResponse> sources = List.of();
            if (directTimeReply != null) {
                reply = directTimeReply;
                model = "system";
                onDelta.accept(reply);
            } else {
                bundle = contextAssembler.assemble(conversation, content,
                        resolved == null ? null : resolved.contextText(), documentText, imageDataUrls,
                        userMessage.getId(), false);
                sources = toSources(bundle.sources());
                StringBuilder streamed = new StringBuilder();
                BusinessException failure = null;
                boolean success = false;
                try {
                    if (imageIds.isEmpty()) {
                        AiChatService.StreamDone[] done = new AiChatService.StreamDone[1];
                        aiChatService.chatStream(bundle.messages(),
                                delta -> {
                                    streamed.append(delta);
                                    onDelta.accept(delta);
                                },
                                streamDone -> done[0] = streamDone);
                        if (done[0] != null) {
                            model = done[0].model();
                            promptTokens = done[0].promptTokens();
                            completionTokens = done[0].completionTokens();
                        }
                        reply = streamed.toString();
                    } else {
                        ChatCall call = callChatOnce(bundle.messages(), true);
                        reply = call.reply();
                        model = call.model();
                        promptTokens = call.promptTokens();
                        completionTokens = call.completionTokens();
                        onDelta.accept(reply);
                    }
                    success = true;
                } catch (BusinessException exception) {
                    com.studenthub.integration.ai.AiCallCancellation.checkCurrent();
                    failure = exception;
                }
                // 只有还没吐出任何增量时才压缩重试，避免前端收到重复内容
                if (!success && imageIds.isEmpty() && streamed.length() == 0 && isContextLength(failure)) {
                    log.warn("流式上下文超限，压缩后重试, conversationId={}, estimated={}",
                            conversation.getId(), bundle.estimatedTokens());
                    bundle = contextAssembler.assemble(conversation, content,
                            resolved == null ? null : resolved.contextText(), documentText, imageDataUrls,
                            userMessage.getId(), true);
                    sources = toSources(bundle.sources());
                    try {
                        AiChatService.StreamDone[] done = new AiChatService.StreamDone[1];
                        StringBuilder retried = new StringBuilder();
                        aiChatService.chatStream(bundle.messages(),
                                delta -> {
                                    retried.append(delta);
                                    onDelta.accept(delta);
                                },
                                streamDone -> done[0] = streamDone);
                        if (done[0] != null) {
                            model = done[0].model();
                            promptTokens = done[0].promptTokens();
                            completionTokens = done[0].completionTokens();
                        }
                        reply = retried.toString();
                        success = true;
                    } catch (BusinessException retryException) {
                        com.studenthub.integration.ai.AiCallCancellation.checkCurrent();
                        failure = retryException;
                    }
                }
                if (!success) {
                    BusinessException exception = failure;
                    log.warn("AI 流式调用失败, conversationId={}, code={}",
                            conversation.getId(), exception == null ? null : exception.getCode());
                    if (resolved != null && resolved.actions() != null && !resolved.actions().isEmpty()) {
                        reply = localFallbackReply(intent, resolved);
                        onDelta.accept(reply);
                    } else {
                        reply = CampusAssistantPrompts.DEGRADED_REPLY;
                        errorCode = exception == null ? ErrorCode.AI_PROVIDER_ERROR.getCode() : exception.getCode();
                        onDelta.accept(reply);
                    }
                }
                tokenUsage = sumTokens(promptTokens, completionTokens);
            }

            AiMessageEntity assistantMessage = new AiMessageEntity();
            assistantMessage.setConversationId(conversation.getId());
            assistantMessage.setRole(ROLE_ASSISTANT);
            assistantMessage.setContent(reply);
            assistantMessage.setActions(actionsJson);
            assistantMessage.setModel(model);
            assistantMessage.setPromptVersion(com.studenthub.modules.aichat.security.AiPromptRuntime.promptVersion());
            assistantMessage.setTokenUsage(tokenUsage);
            assistantMessage.setPromptTokens(promptTokens);
            assistantMessage.setCompletionTokens(completionTokens);
            assistantMessage.setSources(serializeSources(sources));
            assistantMessage.setCostMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));
            assistantMessage.setErrorCode(errorCode);
            assistantMessage.setStatus(errorCode == null ? MESSAGE_STATUS_OK : MESSAGE_STATUS_FAILED);
            messageMapper.insert(assistantMessage);
            recordContextUsage(conversation, promptTokens, bundle == null ? null : bundle.estimatedTokens());
            if (bundle != null && bundle.summaryUpToMessageId() != null) {
                conversationSummaryService.summarizeAsync(conversation.getId(), bundle.summaryUpToMessageId());
            }
            return AiMessageResponse.from(assistantMessage);
    }

    private record ChatCall(String reply, String model, Integer promptTokens, Integer completionTokens) {
    }

    private ChatCall callChatOnce(List<AiMessage> messages, boolean vision) {
        if (vision) {
            AiChatResponse response = aiChatService.chatVision(messages);
            return new ChatCall(response.content(), response.model(),
                    response.promptTokens(), response.completionTokens());
        }
        AiChatResponse response = aiChatService.chat(messages);
        return new ChatCall(response.content(), response.model(),
                response.promptTokens(), response.completionTokens());
    }

    private Integer sumTokens(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        return (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
    }

    /** 供应商上下文超限的兜底识别（各协议文案不同，统一按关键字判断）。 */
    private boolean isContextLength(BusinessException exception) {
        if (exception == null || exception.getMessage() == null) {
            return false;
        }
        String message = exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        return message.contains("context length")
                || message.contains("context_length")
                || message.contains("maximum context")
                || message.contains("too many tokens")
                || message.contains("prompt is too long")
                || (message.contains("context") && message.contains("exceed"))
                || (message.contains("token") && message.contains("exceed"));
    }

    private List<AiSourceResponse> toSources(List<RagTestResponse.RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<AiSourceResponse> sources = new ArrayList<>(hits.size());
        for (RagTestResponse.RagHit hit : hits) {
            String snippet = hit.content() == null ? null : hit.content();
            if (snippet != null && snippet.length() > 200) {
                snippet = snippet.substring(0, 200) + "…";
            }
            sources.add(new AiSourceResponse(hit.fileName(), hit.page(), hit.titlePath(), hit.score(), snippet));
        }
        return sources;
    }

    private String serializeSources(List<AiSourceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception exception) {
            return null;
        }
    }

    /** 记录会话上下文占用；promptTokens 超过窗口 90% 时标记 compact，提示下一轮摘要。 */
    private void recordContextUsage(AiConversationEntity conversation, Integer promptTokens, Integer estimatedTokens) {
        int used = promptTokens != null ? promptTokens : (estimatedTokens == null ? 0 : estimatedTokens);
        var snapshot = AiRuntimeConfig.current();
        int maxContext = snapshot != null ? snapshot.maxContextTokens() : 60000;
        AiConversationEntity update = new AiConversationEntity();
        update.setId(conversation.getId());
        update.setLastPromptTokens(used);
        update.setLastContextTokens(estimatedTokens == null ? used : estimatedTokens);
        if (maxContext > 0 && used > maxContext * 0.9) {
            update.setCompactCount((conversation.getCompactCount() == null ? 0 : conversation.getCompactCount()) + 1);
        }
        update.setVersion(conversation.getVersion());
        conversationMapper.updateById(update);
    }

    private String currentTimeReply(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.contains("课") || text.contains("课程")) {
            return null;
        }
        boolean asksTime = text.contains("现在几点") || text.contains("几点了")
                || text.contains("当前时间") || text.contains("现在时间")
                || text.contains("北京时间");
        if (!asksTime) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        return "现在是北京时间 " + now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")) + "。";
    }

    @Override
    public String transcribeAudio(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "音频文件 ID 不能为空");
        }
        FileStorageService.StoredContent stored = fileStorageService.readContent(fileId);
        return aiSpeechService.transcribe(stored.bytes(), stored.originalName(), stored.contentType());
    }

    /** 服务端按受控 fileId 读取图片并转 data URL（仅限当前用户已上传的文件） */
    private String toImageDataUrl(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片文件 ID 不能为空");
        }
        FileStorageService.StoredContent stored = fileStorageService.readContent(fileId);
        String type = stored.contentType() == null || stored.contentType().isBlank()
                ? "image/jpeg" : stored.contentType();
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(stored.bytes());
    }

    private List<Long> normalizeImageIds(List<Long> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = images.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(SendMessageRequest.MAX_IMAGES)
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        if (distinct.size() > SendMessageRequest.MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "最多附带 " + SendMessageRequest.MAX_IMAGES + " 张图片");
        }
        return distinct;
    }

    private String serializeImageIds(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return null;
        }
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .writeValueAsString(imageIds);
        } catch (Exception exception) {
            log.warn("图片 ID 序列化失败", exception);
            return null;
        }
    }

    /** 文档附件（≤2 份）：服务端读取当前用户已上传文件并抽取文本注入提示词；失败降级为忽略该文档不阻断对话 */
    private String readDocumentText(List<Long> documentIds) {
        StringBuilder block = new StringBuilder();
        for (Long fileId : documentIds) {
            try {
                FileStorageService.StoredContent stored = fileStorageService.readContent(fileId);
                String fileName = stored.originalName() == null ? "文档" : stored.originalName();
                String text = documentParser.parse(stored.bytes(), fileName).stream()
                        .map(com.studenthub.modules.rag.service.KnowledgeDocumentParser.Section::text)
                        .reduce("", (a, b) -> a + "\n" + b)
                        .trim();
                if (text.isEmpty()) continue;
                if (text.length() > MAX_DOCUMENT_CHARACTERS) {
                    text = text.substring(0, MAX_DOCUMENT_CHARACTERS) + "\n…（文档过长已截断）";
                }
                if (block.length() > 0) block.append("\n\n");
                block.append("【用户上传文档：").append(fileName).append("】\n").append(text);
            } catch (Exception exception) {
                log.warn("AI 消息文档附件解析失败, fileId={}", fileId, exception);
            }
        }
        return block.length() == 0 ? null
                : "以下是用户随本条消息上传的文档内容，仅用于回答本次问题：\n\n" + block;
    }

    private List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = documentIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.size() > SendMessageRequest.MAX_DOCUMENTS) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "最多附带 " + SendMessageRequest.MAX_DOCUMENTS + " 份文档");
        }
        return distinct;
    }

    private AiAssistantResolved resolveForIntent(AiIntentMatch intent, AuthenticatedUser user) {
        return switch (intent.intent()) {
            case SCHEDULE_DAY -> assistantDataService.resolveSchedule(user.userId(), intent.date(), intent.dayOfWeek());
            case SCHEDULE_WEEK -> assistantDataService.resolveSchedule(user.userId(), null, null);
            case MY_REQUESTS -> assistantDataService.resolveMyRequests(user.userId(), intent.statusFilter());
            case SERVICE_ACTION -> assistantDataService.resolveServiceItem(intent.itemCode(), user.campusId());
            case FALLBACK -> null;
        };
    }

    private String localFallbackReply(AiIntentMatch intent, AiAssistantResolved resolved) {
        return switch (intent.intent()) {
            case SCHEDULE_DAY, SCHEDULE_WEEK -> "AI 暂时无法总结，但课表信息已经为你查到啦，点下方卡片直接看课表～";
            case MY_REQUESTS -> "AI 暂时无法总结，申请进度已经为你列好啦，点下方卡片直接查看详情～";
            case SERVICE_ACTION -> "AI 暂时开小差了，点下方卡片可以直接办理服务～";
            case FALLBACK -> CampusAssistantPrompts.DEGRADED_REPLY;
        };
    }

    private List<AiMessage> buildPrompt(String currentContent, Long conversationId, String contextText,
                                       List<String> imageDataUrls, Long currentUserMessageId) {
        List<AiMessage> prompt = new ArrayList<>();
        prompt.add(new AiMessage("system", com.studenthub.modules.aichat.security.AiPromptRuntime.systemPrompt()));
        if (contextText != null && !contextText.isBlank()) {
            prompt.add(new AiMessage("system",
                    "以下是系统为你查询到的、仅供本次回答使用的用户数据（仅针对当前登录用户本人，"
                            + "不得泄露给他人，不得编造数据中不存在的字段或数值）。\n" + contextText));
        }
        List<AiMessageEntity> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversationId)
                .ne(currentUserMessageId != null, AiMessageEntity::getId, currentUserMessageId)
                .orderByDesc(AiMessageEntity::getCreatedAt)
                .last("LIMIT " + currentHistoryWindow()));
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessageEntity entity = history.get(i);
            // 历史里的用户图片消息只保留文本，避免重复注入图片撑大上下文
            if (MESSAGE_STATUS_OK.equals(entity.getStatus())) {
                prompt.add(new AiMessage(entity.getRole().toLowerCase(), entity.getContent()));
            }
        }
        prompt.add(new AiMessage("user", currentContent, imageDataUrls));
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
