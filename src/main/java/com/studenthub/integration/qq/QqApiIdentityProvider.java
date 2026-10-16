package com.studenthub.integration.qq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.oauth.AbstractOAuthIdentityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** real 模式：凭 QQ SDK accessToken 反查身份；客户端 openId 可伪造，只认服务端反查值。 */
@Component
@ConditionalOnProperty(prefix = "studenthub.qq", name = "mode", havingValue = "real")
public class QqApiIdentityProvider extends AbstractOAuthIdentityProvider<QqProperties>
        implements QqIdentityProvider {

    public QqApiIdentityProvider(RestClient.Builder restClientBuilder,
                                 QqProperties properties,
                                 ObjectMapper objectMapper) {
        super(restClientBuilder, properties, objectMapper, ErrorCode.QQ_LOGIN_FAILED, "QQ",
                properties.getApiBaseUrl(), properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds());
    }

    @Override
    public QqIdentity exchange(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        return wrapExchange(() -> doExchange(accessToken));
    }

    private QqIdentity doExchange(String accessToken) {
        String body = restClient().get()
                .uri(uriBuilder -> uriBuilder.path("/oauth2.0/me")
                        .queryParam("access_token", accessToken)
                        .queryParam("unionid", 1)
                        .queryParam("fmt", "json")
                        .build())
                .retrieve()
                .body(String.class);
        QqMeResponse response = parseJson(stripJsonp(body), QqMeResponse.class);
        String openId = requireOpenId(response, QqMeResponse::openId, QqMeResponse::errCode);
        return new QqIdentity(openId, response.unionId());
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record QqMeResponse(@JsonProperty("openid") String openId, @JsonProperty("unionid") String unionId,
        @JsonProperty("error") Integer errCode, @JsonProperty("error_description") String errMessage) { }
