package com.studenthub.modules.jwauth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现：本地开发与测试使用。
 */
@Component
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryJwSessionStore implements JwSessionStore {

    private final ConcurrentHashMap<Long, StoredSession> sessions = new ConcurrentHashMap<>();

    private final JwSessionCipher cipher;

    public InMemoryJwSessionStore(JwSessionCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void save(Long userId, String cookieHeader, Duration ttl) {
        sessions.put(userId, new StoredSession(cipher.encrypt(cookieHeader), Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> find(Long userId) {
        StoredSession session = sessions.get(userId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(userId);
            return Optional.empty();
        }
        return Optional.of(cipher.decrypt(session.content()));
    }

    @Override
    public void delete(Long userId) {
        sessions.remove(userId);
    }

    private record StoredSession(String content, Instant expiresAt) {
    }
}
