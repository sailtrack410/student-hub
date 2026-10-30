package com.studenthub.modules.aichat.controller.admin;

import com.studenthub.integration.ai.AiChatRequest;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.integration.ai.AiModelBinding;
import com.studenthub.integration.ai.AiProvider;
import com.studenthub.modules.aichat.service.AiConfigService;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import com.studenthub.modules.rag.service.KnowledgeBaseService;
import com.studenthub.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端 AI 流式对话测试：SSE 推送增量 token，用于测试页签验证
 * 协议分流（OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES）与流式输出是否生效。
 * 不传 providerId 时走默认语言模型；传 providerId（可选 model）则直接按该供应商绑定调用。
 */
@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "管理端-AI 流式测试", description = "管理端 SSE 流式对话测试")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('ai:manage')")
public class AdminAiStreamController {

    private final AiChatService aiChatService;
    private final AiConfigService aiConfigService;
    private final AiProvider aiProvider;
    /** RAG 关闭（如 test profile）时该 bean 不装配，用 Provider 懒解析避免硬依赖阻断上下文启动 */
    private final ObjectProvider<KnowledgeBaseService> knowledgeBaseServiceProvider;
    private final com.studenthub.modules.aichat.service.execution.AiExecutionResources executionResources;

    public record StreamTestRequest(List<Item> messages, Long providerId, String model, Long knowledgeBaseId) {
        public record Item(String role, String content) {
        }
    }

    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话测试", description = "SSE 事件流：delta=增量文本；done=结束（含模型名与 token 用量）；error=失败原因。"
            + "可选 providerId+model 指定供应商与模型，缺省走默认语言模型；"
            + "可选 knowledgeBaseId 联动知识库——先混合检索注入上下文再生成，sources 事件携带召回片段。", operationId = "testChatStream")
    public SseEmitter chatStream(@RequestBody @Valid StreamTestRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        List<AiMessage> messages = request.messages() == null ? List.of() : request.messages().stream()
                .map(m -> new AiMessage(m.role(), m.content()))
                .toList();
        Long providerId = request.providerId();
        Long knowledgeBaseId = request.knowledgeBaseId();
        var session = new com.studenthub.modules.aichat.service.execution.AiStreamSession(emitter, executionResources);
        session.execute(() -> {
            try {
                // 知识库联动：最后一条用户消息做检索，召回片段注入 system 前置消息
                List<AiMessage> effective = messages;
                if (knowledgeBaseId != null) {
                    effective = applyKnowledge(messages, knowledgeBaseId, session);
                }
                final List<AiMessage> finalMessages = effective;
                if (providerId != null) {
                    AiModelBinding binding = aiConfigService.resolveBinding(providerId, request.model());
                    aiProvider.chatStreamWithBinding(binding,
                            new AiChatRequest(binding.model(), finalMessages),
                            delta -> session.send("delta", delta),
                            (model, promptTokens, completionTokens) -> {
                                session.complete("done", doneJson(model, promptTokens, completionTokens));
                            });
                } else {
                    aiChatService.chatStream(finalMessages,
                            delta -> session.send("delta", delta),
                            done -> {
                                session.complete("done", doneJson(done.model(), done.promptTokens(), done.completionTokens()));
                            });
                }
            } catch (java.util.concurrent.CancellationException exception) {
                throw exception;
            } catch (Exception exception) {
                session.complete("error", new com.studenthub.modules.aichat.controller.dto.AiStreamError(
                        com.studenthub.common.ErrorCode.AI_PROVIDER_ERROR.getCode(),
                        exception.getMessage() == null ? "AI 调用失败" : exception.getMessage()));
            }
        });
        return emitter;
    }

    /**
     * 知识库检索注入：取最后一条用户消息做混合检索，召回片段拼成系统上下文插到消息最前。
     * 检索失败不阻断对话（降级为普通对话），sources 事件把引用片段推给前端展示。
     */
    private List<AiMessage> applyKnowledge(List<AiMessage> messages, Long knowledgeBaseId, com.studenthub.modules.aichat.service.execution.AiStreamSession session) {
        String question = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) { question = messages.get(i).content(); break; }
        }
        if (!StringUtils.hasText(question)) return messages;
        KnowledgeBaseService knowledgeBaseService = knowledgeBaseServiceProvider.getIfAvailable();
        if (knowledgeBaseService == null) {
            session.send("error", "知识库服务未启用");
            return messages;
        }
        try {
            KnowledgeBaseEntity base = knowledgeBaseService.requireBase(knowledgeBaseId);
            if (base == null) {
                session.send("error", "知识库不存在");
                return messages;
            }
            List<RagTestResponse.RagHit> hits = knowledgeBaseService.retrieve(List.of(knowledgeBaseId),
                    question, base.getTopK() == null ? 5 : base.getTopK(), base.getScoreThreshold());
            if (hits.isEmpty()) {
                session.send("sources", "[]");
                List<AiMessage> injected = new ArrayList<>(messages.size() + 1);
                injected.add(new AiMessage("system", (StringUtils.hasText(base.getSystemPrompt())
                        ? base.getSystemPrompt() + "\n\n" : "") + "知识库中未检索到相关资料，请如实说明未找到，不要编造。"));
                injected.addAll(messages);
                return injected;
            }
            String context = hits.stream()
                    .map(h -> "【" + h.fileName() + (h.page() != null ? " 第" + h.page() + "页" : "") + "】\n" + h.content())
                    .collect(java.util.stream.Collectors.joining("\n\n"));
            session.send("sources", sourcesJson(hits));
            List<AiMessage> injected = new ArrayList<>(messages.size() + 1);
            injected.add(new AiMessage("system", (StringUtils.hasText(base.getSystemPrompt())
                    ? base.getSystemPrompt() + "\n\n" : "")
                    + "请优先依据下列知识库资料回答；资料没有的内容如实说明，不要编造。回答末尾用「来源：」列出引用的文件名。\n\n资料：\n" + context));
            injected.addAll(messages);
            return injected;
        } catch (java.util.concurrent.CancellationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("知识库检索失败，降级为普通对话: {}", exception.getMessage());
            return messages;
        }
    }

    /** 召回片段 JSON（fileName/page/score/content 摘要），供前端引用区展示 */
    private String sourcesJson(List<RagTestResponse.RagHit> hits) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < hits.size(); i++) {
            RagTestResponse.RagHit h = hits.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
              .append("\"fileName\":").append(jsonString(h.fileName())).append(',')
              .append("\"page\":").append(h.page() == null ? "null" : h.page()).append(',')
              .append("\"score\":").append(h.score()).append(',')
              .append("\"snippet\":").append(jsonString(abbreviate(h.content(), 120)))
              .append('}');
        }
        return sb.append(']').toString();
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(' '); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 显式指定供应商/模型时的绑定解析已下沉 {@link AiConfigService#resolveBinding(Long, String)}（抛异常语义） */

    private String doneJson(String model, Integer promptTokens, Integer completionTokens) {
        return "{\"model\":\"" + (model == null ? "" : model) + "\",\"promptTokens\":"
                + promptTokens + ",\"completionTokens\":" + completionTokens + "}";
    }

}
