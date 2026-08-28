package com.studenthub.modules.identity.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SessionToken create(AuthenticatedUser user, Duration ttl) {
        String rawToken = SecureTokenSupport.generate();
        Instant expiresAt = Instant.now().plus(ttl);
        try {
            redisTemplate.opsForValue().set(key(rawToken), objectMapper.writeValueAsString(user), ttl);
            return new SessionToken(rawToken, expiresAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authenticated session", exception);
        }
    }

    @Override
    public Optional<AuthenticatedUser> find(String rawToken) {
        String value = redisTemplate.opsForValue().get(key(rawToken));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, AuthenticatedUser.class));
        } catch (JsonProcessingException exception) {
            log.error("认证会话反序列化失败, tokenHash={}", SecureTokenSupport.hash(rawToken), exception);
            redisTemplate.delete(key(rawToken));
            return Optional.empty();
        }
    }

    @Override
    public void replace(String rawToken, AuthenticatedUser user) {
        String sessionKey = key(rawToken);
        Long remainingSeconds = redisTemplate.getExpire(sessionKey);
        if (remainingSeconds == null || remainingSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(sessionKey, objectMapper.writeValueAsString(user),
                    Duration.ofSeconds(remainingSeconds));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authenticated session", exception);
        }
    }

    @Override
    public void delete(String rawToken) {
        redisTemplate.delete(key(rawToken));
    }

    private String key(String rawToken) {
        return KEY_PREFIX + SecureTokenSupport.hash(rawToken);
    }
}
