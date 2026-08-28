package com.studenthub.modules.jwauth.security;

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

    @Override
    public void save(Long userId, String cookieHeader, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, cipher.encrypt(cookieHeader), ttl);
    }

    @Override
    public Optional<String> find(Long userId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        return value == null ? Optional.empty() : Optional.of(cipher.decrypt(value));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
