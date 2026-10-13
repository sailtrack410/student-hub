package com.studenthub.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.config.HttpClientConfig;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 供应商统一调用器：默认聊天模型绑定来自 {@link AiRuntimeConfig} 快照，
 * 按供应商懒构建/缓存客户端，管理后台变更配置后下一次调用即使用新连接，无需重启。
 * 直连 HttpClient.Builder 由 {@link HttpClientConfig#directHttpClientBuilder()} 提供。
 *
 * <p>协议分流：OPENAI_CHAT（/chat/completions）、ANTHROPIC_MESSAGES（/messages）、
 * OPENAI_RESPONSES（/responses）；流式（SSE）按供应商 streaming 开关启用。
 * 无进程内快照（如启动早期）时回退到环境变量默认值（AiProperties）。
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    /** 直连 HttpClient.Builder 工厂（HttpClientConfig 提供，不走系统代理），超时由调用方按快照设置 */
    private final HttpClient.Builder directHttpClientBuilder;
    /** providerId:version -> CallClient，配置版本变化后自动重建 */
    private final ConcurrentHashMap<String, CallClient> callClientCache = new ConcurrentHashMap<>();

    public OpenAiCompatibleProvider(AiProperties properties,
                                    ObjectMapper objectMapper,
                                    HttpClient.Builder directHttpClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.directHttpClientBuilder = directHttpClientBuilder;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        CallContext ctx = resolveContext(request);
        Map<String, Object> payload = buildPayload(ctx, request, false);
        return IntegrationExceptions.wrap(() -> {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ctx.baseUrl() + endpointPath(ctx.protocol())))
                    .timeout(Duration.ofSeconds(ctx.readTimeout()))
                    .header("Content-Type", "application/json")
                    .headers(ctx.headers())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = ctx.httpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("AI provider HTTP {}, body={}", response.statusCode(), abbreviate(response.body()));
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "供应商返回 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return parseNonStreaming(ctx.protocol(), root, request.model());
        }, ErrorCode.AI_PROVIDER_ERROR, "AI provider 调用失败");
    }

    @Override
    public void chatStreamWithBinding(AiModelBinding binding, AiChatRequest request,
                                      java.util.function.Consumer<String> onDelta,
                                      TriConsumer<String, Integer, Integer> onDone) {
        CallContext ctx = resolveContext(binding);
        doStream(ctx, request, onDelta, onDone);
    }

    private void doStream(CallContext ctx, AiChatRequest request, java.util.function.Consumer<String> onDelta,
                          TriConsumer<String, Integer, Integer> onDone) {
        // 供应商关闭流式 → 回退阻塞调用一次性回调
        if (!ctx.streaming()) {
            AiChatResponse response = chat(request);
            onDelta.accept(response.content());
            onDone.accept(response.model(), response.promptTokens(), response.completionTokens());
            return;
        }
        Map<String, Object> payload = buildPayload(ctx, request, true);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ctx.baseUrl() + endpointPath(ctx.protocol())))
                    .timeout(Duration.ofSeconds(ctx.readTimeout() + 60L))
                    .header("Content-Type", "application/json")
                    .headers(ctx.headers())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<java.io.InputStream> response =
                    ctx.httpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("AI provider stream HTTP {}, body={}", response.statusCode(), abbreviate(body));
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "供应商返回 HTTP " + response.statusCode());
            }
            String model = request.model();
            Integer promptTokens = null;
            Integer completionTokens = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if ("[DONE]".equals(data)) break;
                    JsonNode node = objectMapper.readTree(data);
                    // Anthropic: message_stop；OpenAI Responses: response.completed
                    String type = node.path("type").asText("");
                    if ("message_stop".equals(type) || "response.completed".equals(type)) break;
                    StreamDelta delta = parseStreamDelta(ctx.protocol(), node);
                    if (delta != null) {
                        if (delta.text() != null && !delta.text().isEmpty()) onDelta.accept(delta.text());
                        if (delta.model() != null) model = delta.model();
                        if (delta.promptTokens() != null) promptTokens = delta.promptTokens();
                        if (delta.completionTokens() != null) completionTokens = delta.completionTokens();
                    }
                }
            }
            onDone.accept(model, promptTokens, completionTokens);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("AI provider 流式调用失败, model={}, protocol={}", request.model(), ctx.protocol(), exception);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        AiEmbeddingBatchResponse batch = embedBatch(new AiEmbeddingBatchRequest(request.model(), List.of(request.input())));
        if (batch.vectors() == null || batch.vectors().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 返回为空");
        }
        return new AiEmbeddingResponse(batch.vectors().get(0), batch.model(), batch.tokenUsage());
    }

    @Override
    public AiEmbeddingBatchResponse embedBatch(AiEmbeddingBatchRequest request) {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        AiModelBinding binding = snapshot == null ? null : snapshot.embedding();
        if (binding == null || !StringUtils.hasText(binding.apiKey())) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "Embedding 模型尚未配置");
        }
        try {
            RestClient client = clientFor(binding, snapshot.connectTimeoutSeconds(), snapshot.readTimeoutSeconds());
            Map<String, Object> payload = Map.of("model", request.model(), "input", request.input());
            String body = client.post().uri("/embeddings").header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + binding.apiKey()).body(payload)
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 返回为空");
            List<JsonNode> items = new ArrayList<>();
            data.forEach(items::add);
            // OpenAI responses include an index; stable sort also preserves providers
            // that omit it while ensuring batched vectors map back to their chunks.
            items.sort(java.util.Comparator.comparingInt(item -> item.path("index").asInt(Integer.MAX_VALUE)));
            List<List<Double>> vectors = new ArrayList<>();
            for (JsonNode item : items) {
                JsonNode vector = item.path("embedding");
                if (!vector.isArray() || vector.isEmpty()) throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 返回向量为空");
                List<Double> values = new ArrayList<>();
                vector.forEach(n -> values.add(n.asDouble()));
                vectors.add(values);
            }
            return new AiEmbeddingBatchResponse(vectors, root.path("model").asText(request.model()),
                    root.path("usage").path("total_tokens").isInt() ? root.path("usage").path("total_tokens").asInt() : null);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { log.error("Embedding 调用失败, model={}", request.model(), e); throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 服务暂时不可用"); }
    }

    // ==================== 协议分流核心 ====================

    /** 一次调用的完整上下文：绑定信息 + 协议 + 超时 + 复用客户端 */
    private record CallContext(AiModelBinding binding, String protocol, boolean streaming,
                               int readTimeout, HttpClient httpClient, String[] headers) {
        String baseUrl() { return binding.baseUrl(); }
    }

    private CallContext resolveContext(AiChatRequest request) {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        AiModelBinding binding;
        boolean enabled;
        if (request.vision()) {
            binding = snapshot != null ? snapshot.vision() : null;
            enabled = snapshot != null ? snapshot.enabled() : false;
        } else {
            binding = snapshot != null ? snapshot.chat() : fallbackBinding();
            enabled = snapshot != null ? snapshot.enabled() : properties.isEnabled();
        }
        if (!enabled || binding == null) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        return buildContext(binding, snapshot);
    }

    /** 显式绑定路径：不经快照绑定选择，仅复用快照的超时/版本构建调用上下文 */
    private CallContext resolveContext(AiModelBinding binding) {
        return buildContext(binding, AiRuntimeConfig.current());
    }

    /** 绑定 + 当前快照 → 调用上下文（客户端按 providerId:version 复用） */
    private CallContext buildContext(AiModelBinding binding, AiRuntimeSnapshot snapshot) {
        if (!StringUtils.hasText(binding.apiKey())) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        int connectTimeout = snapshot != null ? snapshot.connectTimeoutSeconds()
                : properties.getConnectTimeoutSeconds();
        int readTimeout = snapshot != null ? snapshot.readTimeoutSeconds()
                : properties.getReadTimeoutSeconds();
        String protocol = binding.protocolOrDefault();
        return new CallContext(binding, protocol, binding.streaming(), readTimeout,
                httpClientFor(binding, connectTimeout),
                headersFor(protocol, binding.apiKey()));
    }

    /** 各协议的请求头：OpenAI 系 Bearer，Anthropic x-api-key + 版本号 */
    private String[] headersFor(String protocol, String apiKey) {
        return switch (protocol) {
            case AiModelBinding.PROTOCOL_ANTHROPIC_MESSAGES -> new String[]{
                    "x-api-key", apiKey, "anthropic-version", "2023-06-01"};
            default -> new String[]{"Authorization", "Bearer " + apiKey};
        };
    }

    private String endpointPath(String protocol) {
        return switch (protocol) {
            case AiModelBinding.PROTOCOL_ANTHROPIC_MESSAGES -> "/messages";
            case AiModelBinding.PROTOCOL_OPENAI_RESPONSES -> "/responses";
            default -> "/chat/completions";
        };
    }

    /** 按协议构造请求体；system 消息在 Anthropic 提升到顶层 system、Responses 提升到 instructions */
    private Map<String, Object> buildPayload(CallContext ctx, AiChatRequest request, boolean stream) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", request.model());
        List<AiMessage> messages = request.messages();
        switch (ctx.protocol()) {
            case AiModelBinding.PROTOCOL_ANTHROPIC_MESSAGES -> {
                payload.put("max_tokens", 2048);
                String system = systemText(messages);
                if (system != null) payload.put("system", system);
                payload.put("messages", toAnthropicMessages(messages));
                if (stream) payload.put("stream", true);
            }
            case AiModelBinding.PROTOCOL_OPENAI_RESPONSES -> {
                String instructions = systemText(messages);
                if (instructions != null) payload.put("instructions", instructions);
                payload.put("input", toResponsesInput(messages));
                if (stream) payload.put("stream", true);
            }
            default -> {
                payload.put("messages", toOpenAiMessages(messages));
                if (stream) payload.put("stream", true);
            }
        }
        return payload;
    }

    /** 非流式响应按协议解析为统一 AiChatResponse */
    private AiChatResponse parseNonStreaming(String protocol, JsonNode root, String fallbackModel) {
        return switch (protocol) {
            case AiModelBinding.PROTOCOL_ANTHROPIC_MESSAGES -> {
                StringBuilder content = new StringBuilder();
                root.path("content").forEach(block -> {
                    if ("text".equals(block.path("type").asText())) {
                        content.append(block.path("text").asText(""));
                    }
                });
                if (content.isEmpty()) {
                    log.error("Anthropic 返回了空内容");
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
                }
                JsonNode usage = root.path("usage");
                yield new AiChatResponse(content.toString(), root.path("model").asText(fallbackModel),
                        usage.path("input_tokens").isInt() ? usage.path("input_tokens").asInt() : null,
                        usage.path("output_tokens").isInt() ? usage.path("output_tokens").asInt() : null);
            }
            case AiModelBinding.PROTOCOL_OPENAI_RESPONSES -> {
                StringBuilder content = new StringBuilder();
                root.path("output").forEach(item ->
                        item.path("content").forEach(block -> {
                            if ("output_text".equals(block.path("type").asText())) {
                                content.append(block.path("text").asText(""));
                            }
                        }));
                if (content.isEmpty()) {
                    log.error("AI provider 返回了空内容");
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
                }
                JsonNode usage = root.path("usage");
                yield new AiChatResponse(content.toString(), root.path("model").asText(fallbackModel),
                        usage.path("input_tokens").isInt() ? usage.path("input_tokens").asInt() : null,
                        usage.path("output_tokens").isInt() ? usage.path("output_tokens").asInt() : null);
            }
            default -> {
                String content = root.path("choices").path(0).path("message").path("content").asText(null);
                if (content == null || content.isBlank()) {
                    log.error("AI provider 返回了空内容");
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
                }
                JsonNode usage = root.path("usage");
                yield new AiChatResponse(content,
                        root.path("model").asText(fallbackModel),
                        usage.path("prompt_tokens").isInt() ? usage.path("prompt_tokens").asInt() : null,
                        usage.path("completion_tokens").isInt() ? usage.path("completion_tokens").asInt() : null);
            }
        };
    }

    private record StreamDelta(String text, String model, Integer promptTokens, Integer completionTokens) {
    }

    /** 流式单事件按协议解析为增量；非增量事件返回 null */
    private StreamDelta parseStreamDelta(String protocol, JsonNode node) {
        return switch (protocol) {
            case AiModelBinding.PROTOCOL_ANTHROPIC_MESSAGES -> {
                String type = node.path("type").asText("");
                if ("content_block_delta".equals(type) && "text_delta".equals(node.path("delta").path("type").asText())) {
                    yield new StreamDelta(node.path("delta").path("text").asText(""), null, null, null);
                }
                if ("message_start".equals(type)) {
                    yield new StreamDelta(null, node.path("message").path("model").asText(null),
                            node.path("message").path("usage").path("input_tokens").isInt()
                                    ? node.path("message").path("usage").path("input_tokens").asInt() : null, null);
                }
                if ("message_delta".equals(type)) {
                    yield new StreamDelta(null, null, null,
                            node.path("usage").path("output_tokens").isInt()
                                    ? node.path("usage").path("output_tokens").asInt() : null);
                }
                yield null;
            }
            case AiModelBinding.PROTOCOL_OPENAI_RESPONSES -> {
                String type = node.path("type").asText("");
                if ("response.output_text.delta".equals(type)) {
                    yield new StreamDelta(node.path("delta").asText(""), null, null, null);
                }
                if ("response.completed".equals(type)) {
                    JsonNode usage = node.path("response").path("usage");
                    yield new StreamDelta(null, node.path("response").path("model").asText(null),
                            usage.path("input_tokens").isInt() ? usage.path("input_tokens").asInt() : null,
                            usage.path("output_tokens").isInt() ? usage.path("output_tokens").asInt() : null);
                }
                yield null;
            }
            default -> {
                JsonNode delta = node.path("choices").path(0).path("delta");
                String text = delta.path("content").asText(null);
                JsonNode usage = node.path("usage");
                yield new StreamDelta(text,
                        node.path("model").asText(null),
                        usage.path("prompt_tokens").isInt() ? usage.path("prompt_tokens").asInt() : null,
                        usage.path("completion_tokens").isInt() ? usage.path("completion_tokens").asInt() : null);
            }
        };
    }

    /** Anthropic 消息体：system 提升到顶层；user 图片转 url source */
    private List<Map<String, Object>> toAnthropicMessages(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            if ("system".equals(message.role())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.role());
            if (message.hasImages() && "user".equals(message.role())) {
                List<Map<String, Object>> parts = new ArrayList<>();
                if (message.content() != null && !message.content().isBlank()) {
                    parts.add(Map.of("type", "text", "text", message.content()));
                }
                for (String image : message.images()) {
                    parts.add(Map.of("type", "image", "source", Map.of("type", "url", "url", image)));
                }
                item.put("content", parts);
            } else {
                item.put("content", message.content() == null ? "" : message.content());
            }
            result.add(item);
        }
        return result;
    }

    /** Responses API 输入：system 提升到 instructions（调用方处理），其余原样 */
    private List<Map<String, Object>> toResponsesInput(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            if ("system".equals(message.role())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.role());
            item.put("content", message.content() == null ? "" : message.content());
            result.add(item);
        }
        return result;
    }

    private String systemText(List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiMessage message : messages) {
            if ("system".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(message.content());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * 将内部消息转为 OpenAI 兼容结构：
     * user 消息携带图片时 content 为 [{type:text},{type:image_url}] 数组，否则为纯文本。
     */
    private static List<Map<String, Object>> toOpenAiMessages(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.role());
            if (message.hasImages() && "user".equals(message.role())) {
                List<Map<String, Object>> parts = new ArrayList<>();
                if (message.content() != null && !message.content().isBlank()) {
                    parts.add(Map.of("type", "text", "text", message.content()));
                }
                for (String image : message.images()) {
                    parts.add(Map.of("type", "image_url",
                            "image_url", Map.of("url", image)));
                }
                item.put("content", parts);
            } else {
                item.put("content", message.content() == null ? "" : message.content());
            }
            result.add(item);
        }
        return result;
    }

    // ==================== 客户端构建 ====================

    /**
     * 唯一缓存：providerId:version -> CallClient（原生 HttpClient + 其上构建的 RestClient）。
     * 聊天/流式走原生 HttpClient，Embedding 走同一 HttpClient 之上的 RestClient；
     * 缓存键含配置版本，版本推进时整表清空（旧连接全部释放），杜绝按版本累积的缓存泄漏。
     */
    private record CallClient(HttpClient httpClient, RestClient restClient) {
    }

    /** 流式/非流式共用的原生 HttpClient（显式直连，避免系统代理把 AI 请求路由到境外线路） */
    private HttpClient httpClientFor(AiModelBinding binding, int connectTimeout) {
        return callClient(binding, connectTimeout, readTimeoutOf()).httpClient();
    }

    /** Embedding 路径用的 RestClient：同一 HttpClient + 快照读超时 */
    private RestClient clientFor(AiModelBinding binding, int connectTimeout, int readTimeout) {
        return callClient(binding, connectTimeout, readTimeout).restClient();
    }

    private CallClient callClient(AiModelBinding binding, int connectTimeout, int restReadTimeout) {
        int version = versionOf();
        String cacheKey = binding.providerId() + ":" + version;
        // 版本推进时清掉全部旧版本条目（含旧 HttpClient 连接池），多个供应商同版本共存互不驱逐
        callClientCache.keySet().removeIf(key -> !key.endsWith(":" + version));
        return callClientCache.computeIfAbsent(cacheKey, ignored -> {
            HttpClient httpClient = directHttpClientBuilder
                    .connectTimeout(Duration.ofSeconds(connectTimeout))
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(restReadTimeout));
            RestClient restClient = RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(binding.baseUrl())
                    .build();
            return new CallClient(httpClient, restClient);
        });
    }

    /** 当前快照版本；无快照（启动早期）按 0 处理 */
    private int versionOf() {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        return snapshot == null ? 0 : snapshot.version();
    }

    /** 当前快照读超时；无快照回退环境变量配置 */
    private int readTimeoutOf() {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        return snapshot != null ? snapshot.readTimeoutSeconds() : properties.getReadTimeoutSeconds();
    }

    /** 无进程内快照（如启动早期）时回退到环境变量配置 */
    private AiModelBinding fallbackBinding() {
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getModel())) {
            return null;
        }
        return new AiModelBinding(0L, properties.getBaseUrl(), properties.getApiKey(),
                properties.getModel(), AiModelBinding.PROTOCOL_OPENAI_CHAT, true);
    }

    private String abbreviate(String s) {
        if (s == null) return null;
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
