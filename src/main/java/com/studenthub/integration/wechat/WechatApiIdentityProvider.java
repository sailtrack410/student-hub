package com.studenthub.integration.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "studenthub.wechat", name = "mode", havingValue = "real")
public class WechatApiIdentityProvider implements WechatIdentityProvider {

    private final RestClient restClient;
    private final WechatProperties properties;
    private final ObjectMapper objectMapper;

    public WechatApiIdentityProvider(RestClient.Builder restClientBuilder,
                                     WechatProperties properties,
                                     ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.getApiBaseUrl())
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WechatIdentity exchange(String code) {
        validateConfiguration();
        try {
            String responseBody = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            WechatSessionResponse response = parseResponse(responseBody);
            if (response == null || response.openId() == null || response.openId().isBlank()) {
                log.warn("微信身份交换失败, errCode={}", response == null ? "empty" : response.errCode());
                throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            return new WechatIdentity(response.openId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("调用微信身份接口失败", exception);
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }

    private void validateConfiguration() {
        boolean appIdConfigured = StringUtils.hasText(properties.getAppId());
        boolean appSecretConfigured = StringUtils.hasText(properties.getAppSecret());
        if (!appIdConfigured || !appSecretConfigured) {
            log.error("微信登录配置缺失, appIdConfigured={}, appSecretConfigured={}",
                    appIdConfigured, appSecretConfigured);
            throw new BusinessException(ErrorCode.WECHAT_NOT_CONFIGURED);
        }
    }

    private WechatSessionResponse parseResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, WechatSessionResponse.class);
        } catch (JsonProcessingException exception) {
            log.error("微信身份接口返回了无法解析的响应", exception);
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WechatSessionResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("errcode") Integer errCode,
            @JsonProperty("errmsg") String errMessage) {
    }
}
