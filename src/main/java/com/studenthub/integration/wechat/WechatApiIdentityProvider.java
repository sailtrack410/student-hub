package com.studenthub.integration.wechat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.integration.oauth.AbstractOAuthIdentityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** real 模式：小程序 wx.login 的 code 换取 openid（/sns/jscode2session）。 */
@Component
@ConditionalOnProperty(prefix = "studenthub.wechat", name = "mode", havingValue = "real")
public class WechatApiIdentityProvider extends AbstractOAuthIdentityProvider<WechatProperties>
        implements WechatIdentityProvider {

    public WechatApiIdentityProvider(RestClient.Builder restClientBuilder,
                                     WechatProperties properties,
                                     ObjectMapper objectMapper) {
        super(restClientBuilder, properties, objectMapper, ErrorCode.WECHAT_LOGIN_FAILED, "微信",
                properties.getApiBaseUrl(), properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds());
    }

    @Override
    public WechatIdentity exchange(String code) {
        requireConfigured(ErrorCode.WECHAT_NOT_CONFIGURED,
                properties.getAppId(), properties.getAppSecret());
        return wrapExchange(() -> doExchange(code));
    }

    private WechatIdentity doExchange(String code) {
        WechatSessionResponse response = parseJson(restClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/jscode2session")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(String.class), WechatSessionResponse.class);
        return new WechatIdentity(requireOpenId(response,
                WechatSessionResponse::openId, WechatSessionResponse::errCode));
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record WechatSessionResponse(@JsonProperty("openid") String openId,
        @JsonProperty("errcode") Integer errCode,
        @JsonProperty("errmsg") String errMessage) {
}
