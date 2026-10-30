package com.studenthub.modules.location.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.location.config.AmapProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 高德 Web 服务：附近 POI 搜索与逆地理编码。Key 仅保存在后端。 */
@Component
public class AmapLocationClient {
    private final ObjectMapper objectMapper;
    private final AmapProperties properties;

    public AmapLocationClient(ObjectMapper objectMapper, AmapProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode around(double latitude, double longitude, String keyword, int radius) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", requireKey());
        params.put("location", longitude + "," + latitude);
        params.put("radius", String.valueOf(radius));
        params.put("offset", "30");
        params.put("page", "1");
        params.put("extensions", "base");
        if (keyword != null && !keyword.isBlank()) params.put("keywords", keyword.trim());
        return get("/v3/place/around", params);
    }

    public JsonNode reverse(double latitude, double longitude) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", requireKey());
        params.put("location", longitude + "," + latitude);
        params.put("extensions", "base");
        return get("/v3/geocode/regeo", params);
    }

    private JsonNode get(String path, Map<String, String> params) {
        try {
            StringBuilder url = new StringBuilder(properties.getBaseUrl()).append(path).append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) url.append('&');
                first = false;
                url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "位置服务暂时不可用");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"1".equals(root.path("status").asText())) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "位置服务查询失败：" + root.path("info").asText("未知错误"));
            }
            return root;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "位置服务请求失败");
        }
    }

    private String requireKey() {
        String key = properties.getWebServiceKey();
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "位置服务未配置");
        }
        return key;
    }

    /** 管理端连通性测试：用固定坐标发一次逆地理编码，仅返回是否可用，不返回 Key。 */
    public ProbeResult probe() {
        long start = System.currentTimeMillis();
        try {
            reverse(30.572815, 104.066801);
            return new ProbeResult(true, System.currentTimeMillis() - start, "高德 Web 服务 Key 有效");
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage().replaceAll("[\\r\\n]+", " ").trim();
            // 防御性脱敏：底层异常文本可能带出完整 URL（含 key）。
            String key = properties.getWebServiceKey();
            if (key != null && !key.isBlank()) {
                message = message.replace(key, "******");
            }
            return new ProbeResult(false, System.currentTimeMillis() - start, message);
        }
    }

    public record ProbeResult(boolean reachable, long latencyMs, String detail) {
    }
}
