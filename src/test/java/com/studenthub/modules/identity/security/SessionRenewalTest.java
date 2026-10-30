package com.studenthub.modules.identity.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会话滑动续期语义：仅在剩余有效期不足阈值时刷新，且不延长已过期会话 */
class SessionRenewalTest {

    private static final Long USER_ID = 1002L;

    private final InMemorySessionStore store = new InMemorySessionStore();

    private AuthenticatedUser user() {
        return new AuthenticatedUser(USER_ID, 1L, 10L, "student", "开发学生", "STUDENT", "BASIC",
                List.of(new RoleScope("REQUESTER", 10L, null)), List.of(), null, null);
    }

    @Test
    void renewSkippedWhenRemainingIsAboveThreshold() {
        SessionToken token = store.create(user(), Duration.ofDays(30));

        boolean renewed = store.renew(token.accessToken(), USER_ID, Duration.ofDays(30), Duration.ofDays(7));

        assertFalse(renewed);
    }

    @Test
    void renewRefreshesSessionThatIsAboutToExpire() throws InterruptedException {
        SessionToken token = store.create(user(), Duration.ofMillis(200));
        Thread.sleep(50);

        // 剩余约 150ms 远低于阈值，应刷新为 1 小时
        assertTrue(store.renew(token.accessToken(), USER_ID, Duration.ofHours(1), Duration.ofHours(1)));

        // 睡过原始 TTL 后仍然有效，证明有效期已被延长
        Thread.sleep(250);
        assertTrue(store.find(token.accessToken()).isPresent());
    }

    @Test
    void notRenewedWhenThresholdDisabled() {
        SessionToken token = store.create(user(), Duration.ofMillis(200));

        assertFalse(store.renew(token.accessToken(), USER_ID, Duration.ofDays(30), Duration.ZERO));
    }

    @Test
    void notRenewedWhenTtlInvalid() {
        SessionToken token = store.create(user(), Duration.ofDays(1));

        assertFalse(store.renew(token.accessToken(), USER_ID, Duration.ZERO, Duration.ofDays(7)));
        assertFalse(store.renew(token.accessToken(), USER_ID, null, Duration.ofDays(7)));
        assertFalse(store.renew(token.accessToken(), USER_ID, Duration.ofDays(30), null));
    }

    @Test
    void notRenewedForUnknownToken() {
        assertFalse(store.renew("not-a-real-token", USER_ID, Duration.ofDays(30), Duration.ofDays(7)));
    }

    @Test
    void expiredSessionIsNotRenewedAndStaysGone() throws InterruptedException {
        SessionToken token = store.create(user(), Duration.ofMillis(30));
        Thread.sleep(60);

        assertFalse(store.renew(token.accessToken(), USER_ID, Duration.ofDays(30), Duration.ofDays(7)));
        assertTrue(store.find(token.accessToken()).isEmpty());
    }

    @Test
    void renewedSessionKeepsSameUser() throws InterruptedException {
        SessionToken token = store.create(user(), Duration.ofMillis(200));
        Thread.sleep(50);
        store.renew(token.accessToken(), USER_ID, Duration.ofHours(1), Duration.ofHours(1));

        AuthenticatedUser loaded = store.find(token.accessToken()).orElseThrow();
        assertEquals(USER_ID, loaded.userId());
        assertEquals("开发学生", loaded.displayName());
    }
}
