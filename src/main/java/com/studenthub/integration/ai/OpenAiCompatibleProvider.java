package com.studenthub.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI 兼容 AI 供应商：默认聊天模型绑定来自 {@link AiRuntimeConfig} 快照，
 * 按供应商懒构建/缓存 RestClient，管理后台变更配置后下一次调用即使用新连接，无需重启。
 *
 * <p>无进程内快照（如启动早期）时回退到环境变量默认值（AiProperties）。
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    /** providerId:version -> RestClient，配置版本变化后自动重建 */
    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastCacheKey = new AtomicReference<>();

    public OpenAiCompatibleProvider(AiProperties properties,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
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
        if (!StringUtils.hasText(binding.apiKey())) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        int connectTimeout = snapshot != null ? snapshot.connectTimeoutSeconds()
                : properties.getConnectTimeoutSeconds();
        int readTimeout = snapshot != null ? snapshot.readTimeoutSeconds()
                : properties.getReadTimeoutSeconds();
        RestClient client = clientFor(binding, connectTimeout, readTimeout, snapshot == null ? 0 : snapshot.version());
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", request.model());
            payload.put("messages", toOpenAiMessages(request.messages()));

            String responseBody = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + binding.apiKey())
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                log.error("AI provider 返回了空内容, model={}", request.model());
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
            }
            JsonNode usage = root.path("usage");
            return new AiChatResponse(
                    content,
                    root.path("model").asText(request.model()),
                    usage.path("prompt_tokens").isInt() ? usage.path("prompt_tokens").asInt() : null,
                    usage.path("completion_tokens").isInt() ? usage.path("completion_tokens").asInt() : null);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("AI provider 调用失败, model={}", request.model(), exception);
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
            RestClient client = clientFor(binding, snapshot.connectTimeoutSeconds(), snapshot.readTimeoutSeconds(), snapshot.version());
            Map<String, Object> payload = Map.of("model", request.model(), "input", request.input());
            String body = client.post().uri("/embeddings").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + binding.apiKey()).body(payload)
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 返回为空");
            List<JsonNode> items = new java.util.ArrayList<>();
            data.forEach(items::add);
            // OpenAI responses include an index; stable sort also preserves providers
            // that omit it while ensuring batched vectors map back to their chunks.
            items.sort(java.util.Comparator.comparingInt(item -> item.path("index").asInt(Integer.MAX_VALUE)));
            List<List<Double>> vectors = new java.util.ArrayList<>();
            for (JsonNode item : items) {
                JsonNode vector = item.path("embedding");
                if (!vector.isArray() || vector.isEmpty()) throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 返回向量为空");
                List<Double> values = new java.util.ArrayList<>();
                vector.forEach(n -> values.add(n.asDouble()));
                vectors.add(values);
            }
            return new AiEmbeddingBatchResponse(vectors, root.path("model").asText(request.model()),
                    root.path("usage").path("total_tokens").isInt() ? root.path("usage").path("total_tokens").asInt() : null);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { log.error("Embedding 调用失败, model={}", request.model(), e); throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Embedding 服务暂时不可用"); }
    }

    /**
     * 将内部消息转为 OpenAI 兼容结构：
     * user 消息携带图片时 content 为 [{type:text},{type:image_url}] 数组，否则为纯文本。
     */
    private static List<Map<String, Object>> toOpenAiMessages(List<AiMessage> messages) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (AiMessage message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.role());
            if (message.hasImages() && "user".equals(message.role())) {
                List<Map<String, Object>> parts = new java.util.ArrayList<>();
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

    /** 按供应商+配置版本懒构建/重建 RestClient；版本推进后清掉旧连接，避免缓存累积 */
    private RestClient clientFor(AiModelBinding binding, int connectTimeout, int readTimeout, int version) {
        String cacheKey = binding.providerId() + ":" + version;
        String previous = lastCacheKey.getAndSet(cacheKey);
        if (!cacheKey.equals(previous)) {
            clientCache.clear();
        }
        return clientCache.computeIfAbsent(cacheKey, ignored -> {
            // 显式直连：AI 供应商（SiliconFlow/DeepSeek 等）国内可直接访问，
            // 开发机系统代理（如 Clash）会把请求路由到境外线路导致严重变慢
            HttpClient httpClient = HttpClient.newBuilder()
                    .proxy(HttpClient.Builder.NO_PROXY)
                    .connectTimeout(Duration.ofSeconds(connectTimeout))
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));
            return RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(binding.baseUrl())
                    .build();
        });
    }

    /** 无进程内快照（如启动早期）时回退到环境变量配置 */
    private AiModelBinding fallbackBinding() {
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getModel())) {
            return null;
        }
        return new AiModelBinding(0L, properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
    }
}
