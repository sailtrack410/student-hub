package com.studenthub.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 管理端“微信登录连通性测试”：用当前 AppID/AppSecret 调一次
 * {@code /cgi-bin/token}，验证凭证有效，不缓存也不返回 access_token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatCredentialProbe {

    private final ObjectMapper objectMapper;

    public ProbeResult test(String baseUrl, String appId, String appSecret,
                            int connectTimeoutSeconds, int readTimeoutSeconds) {
        long start = System.currentTimeMillis();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            return new ProbeResult(false, 0, "微信 AppID / AppSecret / API 地址未配置完整");
        }
        try {
            String url = baseUrl.replaceAll("/+$", "")
                    + "/cgi-bin/token?grant_type=client_credential"
                    + "&appid=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ProbeResult(false, latency, "微信接口返回 HTTP " + response.statusCode());
            }
            JsonNode body = objectMapper.readTree(response.body());
            String token = body.path("access_token").asText(null);
            if (StringUtils.hasText(token)) {
                return new ProbeResult(true, latency, "微信 access_token 获取成功，AppID/AppSecret 有效");
            }
            int errCode = body.path("errcode").asInt(-1);
            String errMsg = body.path("errmsg").asText("");
            String detail = "微信返回错误 errcode=" + errCode;
            if (StringUtils.hasText(errMsg)) {
                detail += "，errmsg=" + truncate(errMsg);
            }
            log.warn("微信凭证连通性测试失败, errcode={}, errmsg={}", errCode, errMsg);
            return new ProbeResult(false, latency, detail);
        } catch (Exception exception) {
            long latency = System.currentTimeMillis() - start;
            String detail = safeMessage(exception, appSecret);
            log.warn("微信凭证连通性测试异常, error={}", detail);
            return new ProbeResult(false, latency, detail);
        }
    }

    /** 异常信息可能回显完整请求 URL（含 secret），返回前必须脱敏并截断。 */
    private String safeMessage(Exception exception, String appSecret) {
        String detail = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return detail;
        }
        String cleaned = message.replaceAll("[\\r\\n]+", " ").trim();
        if (StringUtils.hasText(appSecret)) {
            cleaned = cleaned.replace(appSecret, "******");
            cleaned = cleaned.replace(URLEncoder.encode(appSecret, StandardCharsets.UTF_8), "******");
        }
        return detail + ": " + truncate(cleaned);
    }

    private String truncate(String value) {
        return value.length() > 120 ? value.substring(0, 120) + "…" : value;
    }

    public record ProbeResult(boolean reachable, long latencyMs, String detail) {
    }
}
