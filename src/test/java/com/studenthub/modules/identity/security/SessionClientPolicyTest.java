package com.studenthub.modules.identity.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会话客户端策略：App 走长期免登录，其余客户端一律短 TTL 且不续期 */
class SessionClientPolicyTest {

    private SecurityProperties properties;
    private SessionClientPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.setSessionTtl(Duration.ofHours(8));
        properties.setAppSessionTtl(Duration.ofDays(30));
        properties.setSessionRenewThreshold(Duration.ofDays(7));
        properties.setAppClientHeader("x-client-type");
        properties.setAppClientValue("app");
        policy = new SessionClientPolicy(properties);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest requestWithClientType(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader("x-client-type", value);
        }
        return request;
    }

    @Test
    void appHeaderIsDetectedIgnoringCaseAndSpaces() {
        assertTrue(policy.isAppClient(requestWithClientType("app")));
        assertTrue(policy.isAppClient(requestWithClientType("APP")));
        assertTrue(policy.isAppClient(requestWithClientType(" App ")));
    }

    @Test
    void otherOrMissingClientTypesAreNotApp() {
        assertFalse(policy.isAppClient(requestWithClientType("web")));
        assertFalse(policy.isAppClient(requestWithClientType("")));
        assertFalse(policy.isAppClient(requestWithClientType(null)));
        assertFalse(policy.isAppClient(null));
    }

    @Test
    void blankHeaderConfigurationNeverMatchesApp() {
        properties.setAppClientHeader(" ");
        assertFalse(policy.isAppClient(requestWithClientType("app")));
        properties.setAppClientHeader("x-client-type");
        properties.setAppClientValue("");
        assertFalse(policy.isAppClient(requestWithClientType("app")));
    }

    @Test
    void creationTtlWithoutRequestContextFallsBackToShortTtl() {
        // 无请求上下文（定时任务、内部调用）时按非 App 处理，避免意外发放长效会话
        assertEquals(Duration.ofHours(8), policy.creationTtl());
    }

    @Test
    void creationTtlUsesLongTtlForAppRequests() {
        MockHttpServletRequest request = requestWithClientType("app");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(Duration.ofDays(30), policy.creationTtl());

        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(requestWithClientType("web")));
        assertEquals(Duration.ofHours(8), policy.creationTtl());
    }

    @Test
    void renewalEnabledOnlyForAppClients() {
        SessionClientPolicy.Renewal appRenewal = policy.renewalFor(requestWithClientType("app"));
        assertTrue(appRenewal.enabled());
        assertEquals(Duration.ofDays(30), appRenewal.ttl());
        assertEquals(Duration.ofDays(7), appRenewal.threshold());

        assertFalse(policy.renewalFor(requestWithClientType("web")).enabled());
        assertFalse(policy.renewalFor(requestWithClientType(null)).enabled());
    }

    @Test
    void renewalDisabledWhenThresholdIsZero() {
        properties.setSessionRenewThreshold(Duration.ZERO);
        assertFalse(policy.renewalFor(requestWithClientType("app")).enabled());
    }
}
