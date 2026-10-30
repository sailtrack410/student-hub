package com.studenthub.modules.aichat.service.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.util.TokenEstimator;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.integration.ai.AiModelBinding;
import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.integration.ai.AiRuntimeSnapshot;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.modules.aichat.mapper.AiModelMapper;
import com.studenthub.modules.aichat.service.CampusAssistantPrompts;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.rag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 AI 请求的上下文装配器（V54）。
 *
 * <p>预算：模型窗口（ai_model，缺省全局 max_context_tokens）− 最大输出 − 安全余量；
 * 再按配置比例分配给 system / 个人数据 / 知识库 / 附件 / 历史；历史按整条消息从新到旧装，
 * 不够时优先丢弃最老的历史；摘要替换更早的历史，避免每轮重复塞全文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextAssembler {

    private final AiMessageMapper messageMapper;
    private final AiModelMapper modelMapper;
    private final ObjectProvider<KnowledgeBaseService> knowledgeBaseProvider;

    /** 装配结果。 */
    public record Bundle(List<AiMessage> messages,
                         int estimatedTokens,
                         int ragTokens,
                         int historyTokens,
                         List<RagTestResponse.RagHit> sources,
                         boolean summaryUsed,
                         Long summaryUpToMessageId) {
    }

    public Bundle assemble(AiConversationEntity conversation,
                           String content,
                           String personalContext,
                           String documentText,
                           List<String> imageDataUrls,
                           Long currentUserMessageId,
                           boolean compact) {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        int maxContext = snapshot != null ? snapshot.maxContextTokens() : 60000;
        int safety = snapshot != null ? snapshot.contextSafetyMarginTokens() : 512;
        int maxOutput = snapshot != null ? snapshot.maxOutputTokens() : 2048;
        double charsPerToken = snapshot != null ? snapshot.tokenEstimateCharsPerToken() : 1.5;
        double historyRatio = snapshot != null ? snapshot.historyTokenRatio() : 0.40;
        double ragRatio = snapshot != null ? snapshot.ragTokenRatio() : 0.25;
        double attachRatio = snapshot != null ? snapshot.attachmentTokenRatio() : 0.15;
        boolean summaryEnabled = snapshot == null || snapshot.summaryEnabled();

        int window = resolveModelWindow(snapshot, maxContext);
        int maxOutputTokens = resolveModelMaxOutput(snapshot, maxOutput);
        int usable = Math.max(1024, Math.min(window, maxContext) - maxOutputTokens - safety);

        List<AiMessage> messages = new ArrayList<>();
        // I04：优先使用管理端激活的数据库提示词版本，未配置时回退代码内置基线
        String systemPrompt = com.studenthub.modules.aichat.security.AiPromptRuntime.systemPrompt();
        messages.add(new AiMessage("system", systemPrompt));
        int used = TokenEstimator.estimate(systemPrompt, charsPerToken);

        // 个人数据 / 意图上下文：最多占可用预算的 40%，超长截断
        if (StringUtils.hasText(personalContext)) {
            int cap = Math.max(0, (int) (usable * 0.40));
            String text = truncateToTokens(personalContext, cap, charsPerToken);
            messages.add(new AiMessage("system",
                    "以下是系统为你查询到的、仅供本次回答使用的用户数据（仅针对当前登录用户本人，"
                            + "不得泄露给他人，不得编造数据中不存在的字段或数值）。\n" + text));
            used += TokenEstimator.estimate(text, charsPerToken) + 12;
        }

        int currentReserve = TokenEstimator.estimate(content, charsPerToken)
                + (imageDataUrls == null ? 0 : imageDataUrls.size() * 300);
        int remaining = Math.max(0, usable - used - currentReserve);

        // 知识库：只对没有图片的消息做 RAG；压缩重试时预算减半
        List<RagTestResponse.RagHit> sources = List.of();
        int ragTokens = 0;
        KnowledgeBaseService kb = imageDataUrls == null || imageDataUrls.isEmpty()
                ? knowledgeBaseProvider.getIfAvailable() : null;
        if (kb != null && remaining > 200) {
            int ragBudget = (int) (usable * ragRatio);
            if (compact) {
                ragBudget /= 2;
            }
            ragBudget = Math.min(ragBudget, Math.max(0, remaining - 200));
            if (ragBudget >= 100) {
                try {
                    KnowledgeBaseService.RagContext rag = kb.assistantContext(content, ragBudget);
                    if (rag.text() != null && !rag.text().isBlank()) {
                        String ragPrompt = "以下是知识库检索到的资料（可能不完整）。回答规则：优先依据资料；"
                                + "资料没有的内容不要编造；回答末尾用「来源：文件名」列出引用。\n\n" + rag.text();
                        messages.add(new AiMessage("system", ragPrompt));
                        sources = rag.hits();
                        ragTokens = TokenEstimator.estimate(ragPrompt, charsPerToken);
                        used += ragTokens;
                        remaining = Math.max(0, remaining - ragTokens);
                    }
                } catch (Exception exception) {
                    log.warn("知识库上下文装配失败，本轮降级为普通对话: {}", exception.getMessage());
                }
            }
        }

        // 附件 / 文档文本：按附件预算截断
        if (StringUtils.hasText(documentText) && remaining > 100) {
            int attachBudget = Math.min((int) (usable * attachRatio), Math.max(0, remaining - 200));
            String text = truncateToTokens(documentText, attachBudget, charsPerToken);
            messages.add(new AiMessage("system", "用户上传文档内容（可能被截断）：\n" + text));
            int attachTokens = TokenEstimator.estimate(text, charsPerToken) + 10;
            used += attachTokens;
            remaining = Math.max(0, remaining - attachTokens);
        }

        // 历史：摘要 + 最近消息（从新到旧装，按条保留，不拆当前轮）
        int historyBudget = Math.max(0, Math.min(remaining, (int) (usable * historyRatio)));
        int historyTokens = 0;
        Long summaryUpTo = null;
        if (StringUtils.hasText(conversation.getSummary())) {
            String summaryPrompt = "此前对话摘要（供参考，不代表本轮事实）：\n" + conversation.getSummary();
            messages.add(new AiMessage("system", summaryPrompt));
            int summaryTokens = TokenEstimator.estimate(summaryPrompt, charsPerToken);
            historyTokens += summaryTokens;
            used += summaryTokens;
            historyBudget = Math.max(0, historyBudget - summaryTokens);
        }
        List<AiMessageEntity> history = loadHistory(conversation, currentUserMessageId, snapshot);
        List<AiMessageEntity> kept = new ArrayList<>();
        int cursor = history.size() - 1;
        while (cursor >= 0) {
            AiMessageEntity message = history.get(cursor);
            if (!"OK".equals(message.getStatus())) {
                cursor--;
                continue;
            }
            int cost = TokenEstimator.estimate(message.getContent(), charsPerToken);
            if (historyTokens + cost > historyBudget) {
                break;
            }
            kept.add(0, message);
            historyTokens += cost;
            cursor--;
        }
        for (AiMessageEntity message : kept) {
            messages.add(new AiMessage(message.getRole().toLowerCase(), message.getContent()));
        }
        // 有更早未进入上下文的历史 → 交给摘要服务压缩（不含当前轮）
        if (summaryEnabled && !history.isEmpty() && cursor >= 0) {
            summaryUpTo = history.get(cursor).getId();
        }
        if (summaryEnabled && summaryUpTo == null && !history.isEmpty()) {
            long summaryUntil = conversation.getSummaryUntilMessageId() == null ? 0L : conversation.getSummaryUntilMessageId();
            Long olderCount = messageMapper.selectCount(new LambdaQueryWrapper<AiMessageEntity>()
                    .eq(AiMessageEntity::getConversationId, conversation.getId())
                    .gt(AiMessageEntity::getId, summaryUntil)
                    .lt(AiMessageEntity::getId, history.get(0).getId()));
            if (olderCount != null && olderCount > 0) {
                summaryUpTo = history.get(0).getId();
            }
        }

        messages.add(new AiMessage("user", content, imageDataUrls));
        int estimated = used + currentReserve;
        return new Bundle(messages, estimated, ragTokens, historyTokens, sources,
                StringUtils.hasText(conversation.getSummary()), summaryUpTo);
    }

    private List<AiMessageEntity> loadHistory(AiConversationEntity conversation, Long currentUserMessageId,
                                              AiRuntimeSnapshot snapshot) {
        int window = snapshot != null ? Math.max(2, snapshot.historyWindow()) : 10;
        long summaryUntil = conversation.getSummaryUntilMessageId() == null ? 0L : conversation.getSummaryUntilMessageId();
        List<AiMessageEntity> desc = messageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversation.getId())
                .gt(AiMessageEntity::getId, summaryUntil)
                .ne(currentUserMessageId != null, AiMessageEntity::getId, currentUserMessageId)
                .orderByDesc(AiMessageEntity::getCreatedAt)
                .last("LIMIT " + Math.max(window * 3, window + 20)));
        List<AiMessageEntity> asc = new ArrayList<>(desc);
        java.util.Collections.reverse(asc);
        return asc;
    }

    private int resolveModelWindow(AiRuntimeSnapshot snapshot, int fallback) {
        if (snapshot != null && snapshot.chat() != null) {
            try {
                AiModelBinding chat = snapshot.chat();
                var model = modelMapper.selectOne(new LambdaQueryWrapper<com.studenthub.modules.aichat.entity.AiModelEntity>()
                        .eq(com.studenthub.modules.aichat.entity.AiModelEntity::getProviderId, chat.providerId())
                        .eq(com.studenthub.modules.aichat.entity.AiModelEntity::getProviderModelId, chat.model())
                        .last("LIMIT 1"));
                if (model != null && model.getContextWindowTokens() != null && model.getContextWindowTokens() > 0) {
                    return model.getContextWindowTokens();
                }
            } catch (Exception exception) {
                log.debug("读取模型上下文窗口失败，回退全局配置: {}", exception.getMessage());
            }
        }
        return fallback;
    }

    private int resolveModelMaxOutput(AiRuntimeSnapshot snapshot, int fallback) {
        if (snapshot != null && snapshot.chat() != null) {
            try {
                AiModelBinding chat = snapshot.chat();
                var model = modelMapper.selectOne(new LambdaQueryWrapper<com.studenthub.modules.aichat.entity.AiModelEntity>()
                        .eq(com.studenthub.modules.aichat.entity.AiModelEntity::getProviderId, chat.providerId())
                        .eq(com.studenthub.modules.aichat.entity.AiModelEntity::getProviderModelId, chat.model())
                        .last("LIMIT 1"));
                if (model != null && model.getMaxOutputTokens() != null && model.getMaxOutputTokens() > 0) {
                    return model.getMaxOutputTokens();
                }
            } catch (Exception exception) {
                log.debug("读取模型最大输出失败，回退全局配置: {}", exception.getMessage());
            }
        }
        return fallback;
    }

    /** 按估算 token 截断字符串（保留头部，加省略标记）。 */
    private String truncateToTokens(String text, int maxTokens, double charsPerToken) {
        if (text == null || maxTokens <= 0) {
            return "";
        }
        if (TokenEstimator.estimate(text, charsPerToken) <= maxTokens) {
            return text;
        }
        int maxChars = Math.max(1, (int) (maxTokens * Math.max(0.8, charsPerToken)));
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n…（内容过长已截断）";
    }
}
