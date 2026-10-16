package com.studenthub.modules.aichat.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.ai.AiModelBinding;
import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.integration.ai.AiRuntimeSnapshot;
import com.studenthub.modules.aichat.service.AiSpeechService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * OpenAI 兼容语音识别（SiliconFlow POST /v1/audio/transcriptions），
 * 使用运行时快照中的 ASR 默认模型（如 Qwen/Qwen3-ASR-1.7B）。
 *
 * 注意：SiliconFlow 的该接口不支持 HTTP chunked 传输编码，必须以已知
 * Content-Length 发送。因此此处手写 multipart 字节，再用 JDK HttpClient
 * 的 {@link HttpRequest.BodyPublishers#ofByteArray(byte[])} 发送（自动带
 * Content-Length），而不走 Spring 的流式 multipart 写出。
 */
@Slf4j
@Service
public class AiSpeechServiceImpl implements AiSpeechService {

    private final ObjectMapper objectMapper;
    /** 直连 HttpClient.Builder 工厂（HttpClientConfig 提供，不走系统代理），超时取运行时快照（动态） */
    private final HttpClient.Builder directHttpClientBuilder;

    public AiSpeechServiceImpl(ObjectMapper objectMapper, HttpClient.Builder directHttpClientBuilder) {
        this.objectMapper = objectMapper;
        this.directHttpClientBuilder = directHttpClientBuilder;
    }

    @Override
    public String transcribe(byte[] audio, String filename, String contentType) {
        AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
        if (snapshot == null || !snapshot.enabled() || snapshot.asr() == null) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED, "语音识别模型尚未配置");
        }
        if (audio == null || audio.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "音频内容为空");
        }
        AiModelBinding binding = snapshot.asr();
        String resolvedFilename = normalizeFilename(filename, contentType);
        String boundary = "----studenthub" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(binding.model(), audio, resolvedFilename, contentType, boundary);
        try {
            // 显式直连：AI 供应商（SiliconFlow/DeepSeek 等）国内可直接访问，
            // 而开发机系统代理（如 Clash）会把这些请求路由到境外线路导致上传超时
            HttpClient httpClient = directHttpClientBuilder
                    .connectTimeout(Duration.ofSeconds(snapshot.connectTimeoutSeconds()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(binding.baseUrl() + "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(snapshot.readTimeoutSeconds()))
                    .header("Authorization", "Bearer " + binding.apiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("语音识别返回非 2xx, model={}, status={}, body={}",
                        binding.model(), response.statusCode(), truncate(response.body()));
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "语音识别服务暂时不可用");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("text").asText(null);
            if (text == null || text.isBlank()) {
                log.error("语音识别返回空文本, model={}", binding.model());
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "语音识别结果为空");
            }
            return text.trim();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("语音识别调用失败, model={}", binding.model(), exception);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "语音识别服务暂时不可用");
        }
    }

    /** 手动构造 multipart/form-data body，保证整段可求长度（不触发 chunked）。 */
    private static byte[] buildMultipartBody(String model, byte[] audio, String filename,
                                             String contentType, String boundary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio == null ? 2048 : audio.length + 2048);
        append(out, "--" + boundary + "\r\n");
        append(out, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        append(out, model + "\r\n");
        append(out, "--" + boundary + "\r\n");
        append(out, "Content-Disposition: form-data; name=\"language\"\r\n\r\n");
        append(out, "zh\r\n");
        append(out, "--" + boundary + "\r\n");
        append(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        append(out, "Content-Type: " + resolveContentType(contentType) + "\r\n\r\n");
        out.writeBytes(audio);
        append(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private static void append(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeFilename(String filename, String contentType) {
        String name = (filename == null || filename.isBlank()) ? "audio" : filename;
        name = name.replaceAll("[\\r\\n\\\\/\"]", "_");
        if (name.contains(".")) {
            return name;
        }
        String ext = extensionOf(contentType);
        return name + (ext == null ? ".mp3" : ext);
    }

    private static String extensionOf(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            case "audio/wav", "audio/x-wav", "audio/wave" -> ".wav";
            case "audio/aac" -> ".aac";
            case "audio/ogg", "audio/opus" -> ".ogg";
            default -> null;
        };
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}