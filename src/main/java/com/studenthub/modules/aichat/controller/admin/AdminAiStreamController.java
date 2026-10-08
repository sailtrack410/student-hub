package com.studenthub.modules.aichat.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiChatRequest;
import com.studenthub.integration.ai.AiMessage;
import com.studenthub.integration.ai.AiModelBinding;
import com.studenthub.integration.ai.AiProvider;
import com.studenthub.modules.aichat.entity.AiModelEntity;
import com.studenthub.modules.aichat.entity.AiProviderEntity;
import com.studenthub.modules.aichat.mapper.AiModelMapper;
import com.studenthub.modules.aichat.mapper.AiProviderMapper;
import com.studenthub.modules.rag.controller.dto.RagTestResponse;
import com.studenthub.modules.rag.entity.KnowledgeBaseEntity;
import com.studenthub.modules.rag.service.KnowledgeBaseService;
import com.studenthub.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理端 AI 流式对话测试：SSE 推送增量 token，用于测试页签验证
 * 协议分流（OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES）与流式输出是否生效。
 * 不传 providerId 时走默认语言模型；传 providerId（可选 model）则直接按该供应商绑定调用。
 */
@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "管理端-AI 流式测试")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('ai:manage')")
public class AdminAiStreamController {

    private final AiChatService aiChatService;
    private final AiProviderMapper providerMapper;
    private final AiModelMapper modelMapper;
    private final AiProvider aiProvider;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-stream-test");
        t.setDaemon(true);
        return t;
    });

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
        streamExecutor.execute(() -> {
            try {
                // 知识库联动：最后一条用户消息做检索，召回片段注入 system 前置消息
                List<AiMessage> effective = messages;
                if (knowledgeBaseId != null) {
                    effective = applyKnowledge(messages, knowledgeBaseId, emitter);
                }
                final List<AiMessage> finalMessages = effective;
                if (providerId != null) {
                    AiModelBinding binding = resolveBinding(providerId, request.model());
                    aiProvider.chatStreamWithBinding(binding,
                            new AiChatRequest(binding.model(), finalMessages),
                            delta -> send(emitter, "delta", delta),
                            (model, promptTokens, completionTokens) -> {
                                send(emitter, "done", doneJson(model, promptTokens, completionTokens));
                                emitter.complete();
                            });
                } else {
                    aiChatService.chatStream(finalMessages,
                            delta -> send(emitter, "delta", delta),
                            done -> {
                                send(emitter, "done", doneJson(done.model(), done.promptTokens(), done.completionTokens()));
                                emitter.complete();
                            });
                }
            } catch (Exception exception) {
                send(emitter, "error", exception.getMessage() == null ? "AI 调用失败" : exception.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 知识库检索注入：取最后一条用户消息做混合检索，召回片段拼成系统上下文插到消息最前。
     * 检索失败不阻断对话（降级为普通对话），sources 事件把引用片段推给前端展示。
     */
    private List<AiMessage> applyKnowledge(List<AiMessage> messages, Long knowledgeBaseId, SseEmitter emitter) {
        String question = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) { question = messages.get(i).content(); break; }
        }
        if (!StringUtils.hasText(question)) return messages;
        try {
            KnowledgeBaseEntity base = knowledgeBaseService.requireBase(knowledgeBaseId);
            if (base == null) {
                send(emitter, "error", "知识库不存在");
                return messages;
            }
            List<RagTestResponse.RagHit> hits = knowledgeBaseService.retrieve(List.of(knowledgeBaseId),
                    question, base.getTopK() == null ? 5 : base.getTopK(), base.getScoreThreshold());
            if (hits.isEmpty()) {
                send(emitter, "sources", "[]");
                List<AiMessage> injected = new ArrayList<>(messages.size() + 1);
                injected.add(new AiMessage("system", (StringUtils.hasText(base.getSystemPrompt())
                        ? base.getSystemPrompt() + "\n\n" : "") + "知识库中未检索到相关资料，请如实说明未找到，不要编造。"));
                injected.addAll(messages);
                return injected;
            }
            String context = hits.stream()
                    .map(h -> "【" + h.fileName() + (h.page() != null ? " 第" + h.page() + "页" : "") + "】\n" + h.content())
                    .collect(java.util.stream.Collectors.joining("\n\n"));
            send(emitter, "sources", sourcesJson(hits));
            List<AiMessage> injected = new ArrayList<>(messages.size() + 1);
            injected.add(new AiMessage("system", (StringUtils.hasText(base.getSystemPrompt())
                    ? base.getSystemPrompt() + "\n\n" : "")
                    + "请优先依据下列知识库资料回答；资料没有的内容如实说明，不要编造。回答末尾用「来源：」列出引用的文件名。\n\n资料：\n" + context));
            injected.addAll(messages);
            return injected;
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

    /** 显式指定供应商/模型时的绑定解析：供应商须启用且已配密钥；模型缺省取其第一个启用模型 */
    private AiModelBinding resolveBinding(Long providerId, String modelId) {
        AiProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在或未启用");
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "该供应商尚未配置 API Key");
        }
        String model = StringUtils.hasText(modelId) ? modelId : null;
        if (model == null) {
            AiModelEntity first = modelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                    .eq(AiModelEntity::getProviderId, providerId)
                    .eq(AiModelEntity::getEnabled, true)
                    .orderByAsc(AiModelEntity::getProviderModelId)
                    .last("LIMIT 1"));
            if (first == null) {
                throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "该供应商下没有启用的模型");
            }
            model = first.getProviderModelId();
        }
        return new AiModelBinding(provider.getId(), provider.getBaseUrl(), provider.getApiKey(),
                model, provider.getProtocol(),
                provider.getStreaming() == null || provider.getStreaming());
    }

    private String doneJson(String model, Integer promptTokens, Integer completionTokens) {
        return "{\"model\":\"" + (model == null ? "" : model) + "\",\"promptTokens\":"
                + promptTokens + ",\"completionTokens\":" + completionTokens + "}";
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        } catch (Exception ignored) {
            // 客户端断开时 send 抛异常，忽略即可；emitter 由超时/complete 回收
        }
    }
}
