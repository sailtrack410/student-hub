package com.studenthub.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "studenthub.ai", name = "enabled", havingValue = "true")
public class OpenAiCompatibleProvider implements AiProvider {

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleProvider(RestClient.Builder restClientBuilder,
                                    AiProperties properties,
                                    ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", request.model());
            payload.put("messages", request.messages());

            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
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
}
