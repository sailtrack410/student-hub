package com.studenthub.integration.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth 身份反查客户端模板方法基类：收编 wechat/qq 两个 Api 提供者逐行同构的
 * HttpClient → JdkClientHttpRequestFactory → RestClient 构造、GET 交换、JSON 解析、
 * openid 校验与异常收敛；子类只声明 baseUrl/端点/请求参数与平台差异
 * （如 QQ 的 JSONP 剥壳）。泛型 T 为各平台自己的 Properties 类型。
 */
@Slf4j
public abstract class AbstractOAuthIdentityProvider<T> {

    /** QQ /oauth2.0/me 默认返回 JSONP（callback({...});），须剥壳；带 fmt=json 时原样返回。 */
    private static final Pattern JSONP_WRAPPER = Pattern.compile(
            "^[^(]*\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL);

    protected final T properties;
    protected final ObjectMapper objectMapper;

    private final RestClient restClient;
    private final ErrorCode loginFailedErrorCode;
    private final String platformName;

    protected AbstractOAuthIdentityProvider(RestClient.Builder restClientBuilder,
                                            T properties,
                                            ObjectMapper objectMapper,
                                            ErrorCode loginFailedErrorCode,
                                            String platformName,
                                            String baseUrl,
                                            int connectTimeoutSeconds,
                                            int readTimeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.loginFailedErrorCode = loginFailedErrorCode;
        this.platformName = platformName;
    }

    /** 异常收敛模板：BusinessException 原样上抛，其余记 error 后转登录失败错误码。 */
    protected <I> I wrapExchange(IntegrationExceptions.ThrowingSupplier<I> action) {
        return IntegrationExceptions.wrap(action, loginFailedErrorCode,
                "调用" + platformName + "身份接口失败");
    }

    /** 校验反查结果携带 openid；缺失时记 warn 并抛登录失败错误码。 */
    protected <R> String requireOpenId(R response, Function<R, String> openId, Function<R, ?> errCode) {
        String value = response == null ? null : openId.apply(response);
        if (!StringUtils.hasText(value)) {
            log.warn("{}身份交换失败, errCode={}", platformName, response == null ? "empty" : errCode.apply(response));
            throw new BusinessException(loginFailedErrorCode);
        }
        return value;
    }

    /** JSON 响应解析：空体返回 null，解析失败转登录失败错误码。 */
    protected <R> R parseJson(String responseBody, Class<R> type) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, type);
        } catch (JsonProcessingException exception) {
            log.error("{}身份接口返回了无法解析的响应", platformName, exception);
            throw new BusinessException(loginFailedErrorCode);
        }
    }

    /** 校验配置齐备，缺失抛「未配置」错误码。 */
    protected void requireConfigured(ErrorCode notConfigured, String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                log.error("{}登录配置缺失", platformName);
                throw new BusinessException(notConfigured);
            }
        }
    }

    /** QQ 返回 JSONP 时剥掉 callback 包裹；微信不经过此钩子。 */
    protected String stripJsonp(String body) {
        if (!StringUtils.hasText(body)) {
            return body;
        }
        Matcher matcher = JSONP_WRAPPER.matcher(body.trim());
        return matcher.matches() ? matcher.group(1) : body;
    }

    protected RestClient restClient() {
        return restClient;
    }
}
