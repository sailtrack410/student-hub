package com.studenthub.modules.aichat.service.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.integration.ai.AiChatResponse;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话摘要：当历史超出上下文预算时，把"较早的对话"压缩成一段摘要写回 ai_conversation，
 * 后续轮次用摘要替代旧消息。异步执行，失败不阻塞回答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private static final int MAX_TRANSCRIPT_CHARS = 12000;
    private static final int MAX_SUMMARY_CHARS = 4000;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiChatService aiChatService;
    private final com.studenthub.modules.aichat.service.execution.AiExecutionResources executionResources;

    /** 请求线程只投递任务；摘要由单线程池串行执行，避免并发覆盖。 */
    public void summarizeAsync(Long conversationId, Long upToMessageId) {
        if (conversationId == null || upToMessageId == null) {
            return;
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { enqueue(conversationId, upToMessageId); }
                    });
        } else {
            enqueue(conversationId, upToMessageId);
        }
    }

    private void enqueue(Long conversationId, Long upToMessageId) {
        try {
        executionResources.executeSummary(() -> {
            try {
                summarize(conversationId, upToMessageId);
            } catch (Exception exception) {
                log.warn("会话摘要生成失败, conversationId={}, upTo={}, error={}",
                        conversationId, upToMessageId, exception.getMessage());
            }
        });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            log.debug("摘要队列已满，跳过本轮, conversationId={}", conversationId);
        }
    }

    void summarize(Long conversationId, Long upToMessageId) {
        AiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        long already = conversation.getSummaryUntilMessageId() == null ? 0L : conversation.getSummaryUntilMessageId();
        if (upToMessageId <= already) {
            return;
        }
        List<AiMessageEntity> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversationId)
                .gt(AiMessageEntity::getId, already)
                .le(AiMessageEntity::getId, upToMessageId)
                .eq(AiMessageEntity::getStatus, "OK")
                .orderByAsc(AiMessageEntity::getCreatedAt)
                .last("LIMIT 200"));
        if (messages.size() < 2) {
            return;
        }
        StringBuilder transcript = new StringBuilder();
        if (StringUtils.hasText(conversation.getSummary())) {
            transcript.append("已有摘要：").append(conversation.getSummary()).append("\n\n");
        }
        for (AiMessageEntity message : messages) {
            String role = "USER".equals(message.getRole()) ? "用户" : "助手";
            String content = message.getContent() == null ? "" : message.getContent();
            if (content.length() > 600) {
                content = content.substring(0, 600) + "…";
            }
            transcript.append(role).append("：").append(content).append('\n');
            if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
                transcript.append("（更早内容已省略）\n");
                break;
            }
        }
        AiChatResponse response = aiChatService.chat(List.of(
                new AiMessage("system", "你是校园服务平台的会话摘要器。把对话压缩成不超过 400 字的中文摘要，"
                        + "保留用户身份/诉求、已确认的事实、数字、日期、单号、结论与未完成事项；"
                        + "不要寒暄，不要编造，不要遗漏关键约束。只输出摘要正文。"),
                new AiMessage("user", transcript.toString())));
        String summary = response == null || response.content() == null ? null : response.content().trim();
        if (!StringUtils.hasText(summary)) {
            return;
        }
        if (summary.length() > MAX_SUMMARY_CHARS) {
            summary = summary.substring(0, MAX_SUMMARY_CHARS);
        }
        AiConversationEntity update = new AiConversationEntity();
        update.setId(conversationId);
        update.setSummary(summary);
        update.setSummaryUntilMessageId(upToMessageId);
        update.setSummaryUpdatedAt(LocalDateTime.now());
        update.setVersion(conversation.getVersion());
        conversationMapper.updateById(update);
        log.info("会话摘要已更新, conversationId={}, upTo={}, length={}", conversationId, upToMessageId, summary.length());
    }
}
