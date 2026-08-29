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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI 兼容 AI 供应商：按 {@link AiRuntimeConfig} 的快照懒构建/重建 RestClient，
 * 管理后台保存配置后下一次调用即使用新连接，无需重启。
 *
 * <p>配置解析顺序：进程内快照（DB，管理后台维护）→ 环境变量默认值（AiProperties）。
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<RestClient> cachedClient = new AtomicReference<>();
    private final AtomicReference<Integer> cachedVersion = new AtomicReference<>(-1);

    public OpenAiCompatibleProvider(AiProperties properties,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        if (snapshot == null) {
            snapshot = fallbackSnapshot();
        }
        if (!snapshot.enabled()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        if (!StringUtils.hasText(snapshot.apiKey())) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        RestClient client = clientFor(snapshot);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", request.model());
            payload.put("messages", request.messages());

            String responseBody = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + snapshot.apiKey())
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

    /** 按配置版本懒构建/重建 RestClient；版本未变则复用 */
    private RestClient clientFor(AiRuntimeSnapshot snapshot) {
        Integer version = snapshot.version();
        RestClient cached = cachedClient.get();
        if (cached != null && version.equals(cachedVersion.get())) {
            return cached;
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(snapshot.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(snapshot.readTimeoutSeconds()));
        RestClient built = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(snapshot.baseUrl())
                .build();
        cachedClient.set(built);
        cachedVersion.set(version);
        return built;
    }

    /** 无进程内快照（如启动早期）时回退到环境变量配置 */
    private AiRuntimeSnapshot fallbackSnapshot() {
        return new AiRuntimeSnapshot(
                properties.isEnabled(),
                "OPENAI_COMPATIBLE",
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getModel(),
                properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds(),
                properties.getMaxInputCharacters(),
                20, 10, 2000,
                0);
    }
}