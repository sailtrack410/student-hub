package com.studenthub.integration.wechat;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WechatApiIdentityProvider 适配器测试。
 * <p>
 * 使用 JDK 内置 HttpServer 作为等价 Mock，验证微信成功响应映射 openid、
 * 微信错误响应转 BusinessException，不发送真实网络请求。
 */
class WechatApiIdentityProviderTest {

    private HttpServer mockServer;
    private WechatApiIdentityProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = mockServer.getAddress().getPort();

        mockServer.createContext("/sns/jscode2session", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String responseBody;

            if (query != null && query.contains("js_code=bad-code")) {
                responseBody = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
            } else if (query != null && query.contains("js_code=empty-openid")) {
                responseBody = "{\"openid\":\"\",\"errcode\":0}";
            } else if (query != null && query.contains("js_code=server-error")) {
                responseBody = "{\"errcode\":-1,\"errmsg\":\"system error\"}";
            } else {
                responseBody = "{\"openid\":\"test-open-id-abc123\",\"session_key\":\"ignored-in-response\"}";
            }

            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        mockServer.start();

        WechatProperties properties = new WechatProperties();
        properties.setAppId("test-app-id");
        properties.setAppSecret("test-app-secret");
        properties.setApiBaseUrl("http://localhost:" + port);

        provider = new WechatApiIdentityProvider(RestClient.builder(), properties);
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void exchangeReturnsOpenIdOnSuccess() {
        WechatIdentity identity = provider.exchange("valid-code");

        assertEquals("test-open-id-abc123", identity.openId());
    }

    @Test
    void exchangeThrowsWhenWechatReturnsErrorCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.exchange("bad-code"));
        assertEquals(ErrorCode.WECHAT_LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void exchangeThrowsWhenOpenIdIsBlank() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.exchange("empty-openid"));
        assertEquals(ErrorCode.WECHAT_LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void exchangeThrowsWhenWechatReturnsSystemError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.exchange("server-error"));
        assertEquals(ErrorCode.WECHAT_LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void defaultCampusIdReturnsConfiguredValue() {
        assertEquals(1L, provider.defaultCampusId());
    }
}
