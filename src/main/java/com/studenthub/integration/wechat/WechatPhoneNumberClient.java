package com.studenthub.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.wechat.WechatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * 微信手机号获取客户端：小程序 button open-type=getPhoneNumber 的 code 换取绑定手机号。
 * 需要小程序完成微信认证（非个人主体）；未认证环境会返回失败，由前端降级为手动填写。
 */
@Slf4j
@Component
public class WechatPhoneNumberClient {

    private final WechatProperties properties;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private volatile String cachedCredentialFingerprint;

    public WechatPhoneNumberClient(WechatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 用 getPhoneNumber 事件 code 换取用户绑定手机号 */
    public String fetchPhone(String jsCode) {
        if (!"real".equalsIgnoreCase(properties.getMode())) {
            throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
        }
        return IntegrationExceptions.wrap(() -> {
            String accessToken = accessToken();
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            properties.getApiBaseUrl() + "/wa_business/getPhoneNumber?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"" + jsCode + "\"}"))
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            int errCode = body.path("errcode").asInt(0);
            if (errCode != 0) {
                log.warn("微信手机号获取失败, errcode={}, errmsg={}", errCode, body.path("errmsg").asText());
                throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
            }
            String phone = body.path("phone_info").path("purePhoneNumber").asText(null);
            if (phone == null || phone.isBlank()) {
                throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
            }
            return phone;
        }, ErrorCode.PHONE_EXCHANGE_FAILED, "微信手机号接口异常");
    }

    private String accessToken() {
        String fingerprint = properties.getAppId() + "|" + properties.getAppSecret();
        if (!fingerprint.equals(cachedCredentialFingerprint)) {
            // AppID/Secret 在管理端热更新后，旧 access_token 不能继续使用。
            cachedAccessToken = null;
            tokenExpiresAt = Instant.EPOCH;
            cachedCredentialFingerprint = fingerprint;
        }
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }
        return IntegrationExceptions.wrap(() -> {
            HttpResponse<String> response = httpClient().send(
                    HttpRequest.newBuilder(URI.create(
                                    properties.getApiBaseUrl() + "/cgi-bin/token?grant_type=client_credential"
                                            + "&appid=" + properties.getAppId()
                                            + "&secret=" + properties.getAppSecret()))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            String token = body.path("access_token").asText(null);
            if (token == null) {
                log.warn("微信 access_token 获取失败, errcode={}, errmsg={}",
                        body.path("errcode").asInt(), body.path("errmsg").asText());
                throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
            }
            cachedAccessToken = token;
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(300, body.path("expires_in").asLong(7200) - 300));
            return token;
        }, ErrorCode.PHONE_EXCHANGE_FAILED, "微信 access_token 获取异常");
    }

    /** 每次请求按当前配置构建，管理端修改连接超时后无需重启。 */
    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .build();
    }
}
