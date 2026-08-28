package com.studenthub.modules.jwauth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore.JwSession;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class InMemoryJwSessionStore implements JwSessionStore {

    private final ConcurrentHashMap<Long, StoredSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final JwSessionCipher cipher;

    @Override
    public void save(Long userId, JwSession session, Duration ttl) {
        sessions.put(userId, new StoredSession(cipher.encrypt(toJson(session)), Instant.now().plus(ttl)));
    }

    @Override
    public Optional<JwSession> find(Long userId) {
        StoredSession stored = sessions.get(userId);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.expiresAt().isBefore(Instant.now())) {
            sessions.remove(userId);
            return Optional.empty();
        }
        return Optional.of(fromJson(cipher.decrypt(stored.content())));
    }

    @Override
    public void delete(Long userId) {
        sessions.remove(userId);
    }

    private String toJson(JwSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception exception) {
            throw new IllegalStateException("教务会话序列化失败", exception);
        }
    }

    private JwSession fromJson(String content) {
        try {
            return objectMapper.readValue(content, JwSession.class);
        } catch (Exception exception) {
            throw new IllegalStateException("教务会话反序列化失败", exception);
        }
    }

    private record StoredSession(String content, Instant expiresAt) {
    }
}
