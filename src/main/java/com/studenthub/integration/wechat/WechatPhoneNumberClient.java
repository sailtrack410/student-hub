package com.studenthub.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.wechat.WechatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "studenthub.wechat", name = "mode", havingValue = "real")
public class WechatPhoneNumberClient {

    private final WechatProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public WechatPhoneNumberClient(WechatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 用 getPhoneNumber 事件 code 换取用户绑定手机号 */
    public String fetchPhone(String jsCode) {
        try {
            String accessToken = accessToken();
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.weixin.qq.com/wa_business/getPhoneNumber?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"" + jsCode + "\"}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("微信手机号接口异常", exception);
            throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
        }
    }

    private String accessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(
                                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
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
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("微信 access_token 获取异常", exception);
            throw new BusinessException(ErrorCode.PHONE_EXCHANGE_FAILED);
        }
    }
}
