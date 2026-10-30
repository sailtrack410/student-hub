package com.studenthub.integration.qq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.oauth.AbstractOAuthIdentityProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * real 模式：凭 QQ SDK accessToken 反查身份；客户端 openId 可伪造，只认服务端反查值。
 * 反查后校验 token 应用归属：优先比对 /me 返回的 client_id；缺失时再用 APP ID +
 * openid + token 调 get_user_info 兜底，避免拿其他应用的 token 在本平台建号。
 * 同时读取 QQ 昵称和头像，供首次登录/未自定义资料的用户展示。
 */
@Slf4j
@Component
public class QqApiIdentityProvider extends AbstractOAuthIdentityProvider<QqProperties>
        implements QqIdentityProvider {

    public QqApiIdentityProvider(RestClient.Builder restClientBuilder,
                                 QqProperties properties,
                                 ObjectMapper objectMapper) {
        super(restClientBuilder, properties, objectMapper, ErrorCode.QQ_LOGIN_FAILED, "QQ",
                properties.getApiBaseUrl(), properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds());
    }

    /** 管理端保存 QQ 配置后热刷新 baseUrl/超时；客户端按 mode 路由到本 Provider。 */
    public void reload() {
        refreshClient(properties.getApiBaseUrl(), properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds());
    }

    @Override
    public QqIdentity exchange(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        // 移动端 SDK 流程只依赖 APP ID 校验 token 归属；APP KEY 预留给后续网站授权码换 token。
        requireConfigured(ErrorCode.QQ_NOT_CONFIGURED, properties.getClientId());
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
        if (StringUtils.hasText(response.clientId())) {
            // /me 已证明 token 属于本应用；用户资料只是锦上添花，读取失败仍允许登录。
            if (!response.clientId().equals(properties.getClientId())) {
                log.warn("QQ accessToken 属于其他应用, 拒绝登录");
                throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
            }
            QqUserInfoResponse userInfo = fetchUserInfoQuietly(accessToken, openId);
            return toIdentity(openId, response.unionId(), userInfo);
        }
        // 少数移动端响应不带 client_id：必须用 APP ID + openid + token 三元组
        // 调 get_user_info 校验归属，同时拿到昵称/头像；校验失败不能建号。
        QqUserInfoResponse userInfo = fetchUserInfo(accessToken, openId);
        return toIdentity(openId, response.unionId(), userInfo);
    }

    private QqIdentity toIdentity(String openId, String unionId, QqUserInfoResponse userInfo) {
        return new QqIdentity(openId, unionId,
                userInfo == null ? null : normalizeNickname(userInfo.nickname()),
                userInfo == null ? null : normalizeAvatarUrl(userInfo));
    }

    /**
     * 获取 QQ 用户资料；ret 非 0 或响应为空均视为失败。
     * 兼容极少数不返回 ret 但返回了用户信息的响应，只有完全空响应才失败。
     */
    private QqUserInfoResponse fetchUserInfo(String accessToken, String openId) {
        String body = restClient().get()
                .uri(uriBuilder -> uriBuilder.path("/user/get_user_info")
                        .queryParam("access_token", accessToken)
                        .queryParam("oauth_consumer_key", properties.getClientId())
                        .queryParam("openid", openId)
                        .build())
                .retrieve()
                .body(String.class);
        QqUserInfoResponse userInfo = parseJson(stripJsonp(body), QqUserInfoResponse.class);
        if (userInfo == null
                || (userInfo.ret() != null && userInfo.ret() != 0)
                || (userInfo.ret() == null && !userInfo.hasAnyProfileField())) {
            log.warn("QQ 用户资料读取失败, ret={}", userInfo == null ? "empty" : userInfo.ret());
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        return userInfo;
    }

    /** client_id 已通过时资料读取失败不影响登录，但记录告警便于排查授权范围/网络问题。 */
    private QqUserInfoResponse fetchUserInfoQuietly(String accessToken, String openId) {
        try {
            return fetchUserInfo(accessToken, openId);
        } catch (RuntimeException exception) {
            log.warn("QQ 用户资料读取失败，将使用默认昵称/头像: {}", exception.getMessage());
            return null;
        }
    }

    private String normalizeNickname(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String nickname = raw.trim();
        if (nickname.codePointCount(0, nickname.length()) <= 32) {
            return nickname;
        }
        return nickname.substring(0, nickname.offsetByCodePoints(0, 32));
    }

    /** 优先 100×100 的 figureurl_qq_2；QQ 头像地址是 http 的，真机默认禁止明文 HTTP，统一升为 https。 */
    private String normalizeAvatarUrl(QqUserInfoResponse userInfo) {
        String raw = firstNonBlank(userInfo.figureurlQq2(), userInfo.figureurlQq1(),
                userInfo.figureurl2(), userInfo.figureurl1(), userInfo.figureurl());
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String url = raw.trim();
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        return url.startsWith("https://") ? url : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record QqMeResponse(@JsonProperty("openid") String openId, @JsonProperty("unionid") String unionId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("error") Integer errCode, @JsonProperty("error_description") String errMessage) { }

@JsonIgnoreProperties(ignoreUnknown = true)
record QqUserInfoResponse(
        @JsonProperty("ret") Integer ret,
        @JsonProperty("msg") String message,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("figureurl") String figureurl,
        @JsonProperty("figureurl_1") String figureurl1,
        @JsonProperty("figureurl_2") String figureurl2,
        @JsonProperty("figureurl_qq_1") String figureurlQq1,
        @JsonProperty("figureurl_qq_2") String figureurlQq2) {

    boolean hasAnyProfileField() {
        return StringUtils.hasText(nickname) || StringUtils.hasText(figureurl)
                || StringUtils.hasText(figureurl1) || StringUtils.hasText(figureurl2)
                || StringUtils.hasText(figureurlQq1) || StringUtils.hasText(figureurlQq2);
    }
}
