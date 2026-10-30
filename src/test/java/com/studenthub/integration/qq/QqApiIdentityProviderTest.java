package com.studenthub.integration.qq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QQ real 模式反查与 APP ID 归属校验；用本地 HTTP 服务模拟 graph.qq.com，不访问真实 QQ。 */
class QqApiIdentityProviderTest {

    private HttpServer server;
    private int port;
    private volatile String meBody = "{\"client_id\":\"APP_ID_10001\",\"openid\":\"OPENID_1\",\"unionid\":\"UNION_1\"}";
    private volatile String userInfoBody = "{\"ret\":0,\"msg\":\"ok\",\"nickname\":\"测试用户\","
            + "\"figureurl_qq_1\":\"http://thirdqq.qlogo.cn/g?b=qq&nk=1&s=40\","
            + "\"figureurl_qq_2\":\"http://thirdqq.qlogo.cn/g?b=qq&nk=1&s=100\"}";
    private final AtomicInteger meCalls = new AtomicInteger();
    private final AtomicInteger userInfoCalls = new AtomicInteger();
    private final AtomicReference<String> userInfoQuery = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/oauth2.0/me", exchange -> {
            meCalls.incrementAndGet();
            respond(exchange, meBody);
        });
        server.createContext("/user/get_user_info", exchange -> {
            userInfoCalls.incrementAndGet();
            userInfoQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, userInfoBody);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void exchangesTokenAndVerifiesAppIdAndOpenId() {
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        QqIdentity identity = provider.exchange("ACCESS_TOKEN_1");

        assertEquals("OPENID_1", identity.openId());
        assertEquals("UNION_1", identity.unionId());
        assertEquals("测试用户", identity.nickname());
        assertEquals("https://thirdqq.qlogo.cn/g?b=qq&nk=1&s=100", identity.avatarUrl(),
                "优先使用 figureurl_qq_2，并将 http 地址升级为 https");
        assertEquals(1, meCalls.get());
        assertEquals(1, userInfoCalls.get(), "需要额外调用 get_user_info 读取昵称/头像");
    }

    @Test
    void keepsLoginWhenProfileFetchFailsAfterClientIdVerified() {
        userInfoBody = "{\"ret\":100030,\"msg\":\"access token check failed\"}";
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        QqIdentity identity = provider.exchange("ACCESS_TOKEN_1");

        assertEquals("OPENID_1", identity.openId());
        org.junit.jupiter.api.Assertions.assertNull(identity.nickname());
        org.junit.jupiter.api.Assertions.assertNull(identity.avatarUrl());
        assertEquals(1, userInfoCalls.get());
    }

    @Test
    void fallsBackToUserInfoWhenMeOmitsClientId() {
        meBody = "{\"openid\":\"OPENID_1\",\"unionid\":\"UNION_1\"}";
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        QqIdentity identity = provider.exchange("ACCESS_TOKEN_1");
        assertEquals("OPENID_1", identity.openId());
        assertEquals("测试用户", identity.nickname());

        assertEquals(1, userInfoCalls.get());
        String query = userInfoQuery.get();
        assertTrue(query.contains("oauth_consumer_key=APP_ID_10001"), "兜底校验必须带 APP ID: " + query);
        assertTrue(query.contains("openid=OPENID_1"), "兜底校验必须带服务端反查的 openid: " + query);
    }

    @Test
    void rejectsTokenBelongingToAnotherAppByClientId() {
        meBody = "{\"client_id\":\"OTHER_APP\",\"openid\":\"OPENID_1\"}";
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> provider.exchange("FOREIGN_TOKEN"));
        assertEquals(ErrorCode.QQ_LOGIN_FAILED.getCode(), exception.getCode());
        assertEquals(0, userInfoCalls.get(), "client_id 不匹配时不应继续查用户信息");
    }

    @Test
    void acceptsJsonpWrappedMeResponse() {
        meBody = "callback( {\"client_id\":\"APP_ID_10001\",\"openid\":\"OPENID_2\",\"unionid\":\"UNION_2\"} );";
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        assertEquals("OPENID_2", provider.exchange("ACCESS_TOKEN_2").openId());
    }

    @Test
    void rejectsTokenWhenUserInfoBelongsToAnotherApp() {
        meBody = "{\"openid\":\"OPENID_1\"}";
        userInfoBody = "{\"ret\":100030,\"msg\":\"access token check failed\"}";
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> provider.exchange("FOREIGN_TOKEN"));
        assertEquals(ErrorCode.QQ_LOGIN_FAILED.getCode(), exception.getCode());
    }

    @Test
    void failsFastWhenRealModeHasNoAppId() {
        QqApiIdentityProvider provider = provider("");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> provider.exchange("ACCESS_TOKEN_1"));
        assertEquals(ErrorCode.QQ_NOT_CONFIGURED.getCode(), exception.getCode());
        assertEquals(0, meCalls.get(), "缺少 APP ID 时不应发起外部请求");
    }

    @Test
    void rejectsExpiredOrInvalidTokenFromMeEndpoint() {
        meBody = "{\"error\":100014,\"error_description\":\"access token expired\"}";
        QqApiIdentityProvider provider = provider("APP_ID_10001");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> provider.exchange("EXPIRED_TOKEN"));
        assertEquals(ErrorCode.QQ_LOGIN_FAILED.getCode(), exception.getCode());
        assertEquals(0, userInfoCalls.get(), "me 反查失败后不应继续查用户信息");
    }

    private QqApiIdentityProvider provider(String clientId) {
        QqProperties properties = new QqProperties();
        properties.setMode("real");
        properties.setClientId(clientId);
        properties.setApiBaseUrl("http://127.0.0.1:" + port);
        properties.setConnectTimeoutSeconds(2);
        properties.setReadTimeoutSeconds(2);
        return new QqApiIdentityProvider(RestClient.builder(), properties, new ObjectMapper());
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
