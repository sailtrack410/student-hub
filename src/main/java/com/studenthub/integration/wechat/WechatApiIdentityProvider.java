package com.studenthub.integration.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "studenthub.wechat", name = "mode", havingValue = "real")
public class WechatApiIdentityProvider implements WechatIdentityProvider {

    private final RestClient restClient;
    private final WechatProperties properties;

    public WechatApiIdentityProvider(RestClient.Builder restClientBuilder, WechatProperties properties) {
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
    }

    @Override
    public WechatIdentity exchange(String code) {
        try {
            WechatSessionResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(WechatSessionResponse.class);
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

    private record WechatSessionResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("errcode") Integer errCode,
            @JsonProperty("errmsg") String errMessage) {
    }
}
