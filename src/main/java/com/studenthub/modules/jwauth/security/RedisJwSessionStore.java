package com.studenthub.modules.jwauth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.jwauth.security.JwSessionStore.JwSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 实现：Docker 与生产使用；值已加密，Redis 中无明文 Cookie。
 */
@Component
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "redis")
@RequiredArgsConstructor
public class RedisJwSessionStore implements JwSessionStore {

    private static final String KEY_PREFIX = "jw:session:";

    private final StringRedisTemplate redisTemplate;
    private final JwSessionCipher cipher;
    private final ObjectMapper objectMapper;

    @Override
    public void save(Long userId, JwSession session, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, cipher.encrypt(objectMapper.writeValueAsString(session)), ttl);
        } catch (Exception exception) {
            throw new IllegalStateException("教务会话存储失败", exception);
        }
    }

    @Override
    public Optional<JwSession> find(Long userId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cipher.decrypt(value), JwSession.class));
        } catch (Exception exception) {
            throw new IllegalStateException("教务会话反序列化失败", exception);
        }
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
