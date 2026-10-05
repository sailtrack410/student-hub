package com.studenthub.integration.qq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * real 模式：凭客户端 QQ SDK 授权得到的 accessToken 反查 QQ 侧身份。
 *
 * 安全要点：客户端提交的 openId 可伪造，本类只认 https://graph.qq.com/oauth2.0/me
 * 反查返回的 openid，与客户端提交值比对由服务层完成。
 * 接口坑：/oauth2.0/me 默认返回 JSONP（callback( {...} );），须剥壳；
 * /oauth2.0/token 不带 fmt=json 时返回 query string（App 端 SDK 直拿 token 不经过它，
 * 未来做网站授权登录时注意）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "studenthub.qq", name = "mode", havingValue = "real")
public class QqApiIdentityProvider implements QqIdentityProvider {

    private static final Pattern JSONP_WRAPPER = Pattern.compile(
            "^[^(]*\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL);

    private final RestClient restClient;
    private final QqProperties properties;
    private final ObjectMapper objectMapper;

    public QqApiIdentityProvider(RestClient.Builder restClientBuilder,
                                 QqProperties properties,
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
    public QqIdentity exchange(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth2.0/me")
                        .queryParam("access_token", accessToken)
                        .queryParam("unionid", 1)
                        .queryParam("fmt", "json")
                        .build())
                .retrieve()
                .body(String.class);
        QqMeResponse response = parse(stripJsonp(body));
        if (response == null || !StringUtils.hasText(response.openId())) {
            log.warn("QQ 身份反查失败, errCode={}", response == null ? "empty" : response.errCode());
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        return new QqIdentity(response.openId(), response.unionId());
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }

    /** QQ 返回 JSONP 时剥掉 callback 包裹；带 fmt=json 时原样返回。 */
    private String stripJsonp(String body) {
        if (!StringUtils.hasText(body)) {
            return body;
        }
        Matcher matcher = JSONP_WRAPPER.matcher(body.trim());
        return matcher.matches() ? matcher.group(1) : body;
    }

    private QqMeResponse parse(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, QqMeResponse.class);
        } catch (JsonProcessingException exception) {
            log.error("QQ 身份接口返回了无法解析的响应: {}", body);
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QqMeResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("unionid") String unionId,
            @JsonProperty("error") Integer errCode,
            @JsonProperty("error_description") String errMessage) {
    }
}
