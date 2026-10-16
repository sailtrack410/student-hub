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
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "auth:session:";
    private static final String USER_INDEX_PREFIX = "auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SessionToken create(AuthenticatedUser user, Duration ttl) {
        String rawToken = SecureTokenSupport.generate();
        Instant expiresAt = Instant.now().plus(ttl);
        String tokenHash = SecureTokenSupport.hash(rawToken);
        try {
            redisTemplate.opsForValue().set(sessionKey(tokenHash), objectMapper.writeValueAsString(user), ttl);
            redisTemplate.opsForSet().add(userIndexKey(user.userId()), tokenHash);
            redisTemplate.expire(userIndexKey(user.userId()), ttl);
            return new SessionToken(rawToken, expiresAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authenticated session", exception);
        }
    }

    @Override
    public Optional<AuthenticatedUser> find(String rawToken) {
        String value = redisTemplate.opsForValue().get(sessionKey(SecureTokenSupport.hash(rawToken)));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, AuthenticatedUser.class));
        } catch (JsonProcessingException exception) {
            log.error("认证会话反序列化失败, tokenHash={}", SecureTokenSupport.hash(rawToken), exception);
            redisTemplate.delete(sessionKey(SecureTokenSupport.hash(rawToken)));
            return Optional.empty();
        }
    }

    @Override
    public void replace(String rawToken, AuthenticatedUser user) {
        String sessionKey = sessionKey(SecureTokenSupport.hash(rawToken));
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
        redisTemplate.delete(sessionKey(SecureTokenSupport.hash(rawToken)));
    }

    @Override
    public boolean renew(String rawToken, Long userId, Duration ttl, Duration threshold) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()
                || threshold == null || threshold.isNegative() || threshold.isZero()) {
            return false;
        }
        String tokenHash = SecureTokenSupport.hash(rawToken);
        String sessionKey = sessionKey(tokenHash);
        Long remainingSeconds = redisTemplate.getExpire(sessionKey);
        if (remainingSeconds == null || remainingSeconds <= 0) {
            return false;
        }
        if (remainingSeconds > threshold.toSeconds()) {
            return false;
        }
        redisTemplate.expire(sessionKey, ttl);
        // 用户会话索引与主会话同寿命：SADD 幂等，索引已过期时会重新建立
        String indexKey = userIndexKey(userId);
        redisTemplate.opsForSet().add(indexKey, tokenHash);
        redisTemplate.expire(indexKey, ttl);
        return true;
    }

    @Override
    public int refreshUserSessions(Long userId, AuthenticatedUser refreshed) {
        String indexKey = userIndexKey(userId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(indexKey);
        if (tokenHashes == null || tokenHashes.isEmpty()) {
            return 0;
        }
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(refreshed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authenticated session", exception);
        }
        int count = 0;
        for (String tokenHash : tokenHashes) {
            String sessionKey = sessionKey(tokenHash);
            Long remainingSeconds = redisTemplate.getExpire(sessionKey);
            if (remainingSeconds == null || remainingSeconds <= 0) {
                // 会话已过期，顺手清理索引残留
                redisTemplate.opsForSet().remove(indexKey, tokenHash);
                continue;
            }
            redisTemplate.opsForValue().set(sessionKey, serialized, Duration.ofSeconds(remainingSeconds));
            count++;
        }
        return count;
    }

    @Override
    public int deleteUserSessions(Long userId) {
        String indexKey = userIndexKey(userId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(indexKey);
        if (tokenHashes == null || tokenHashes.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String tokenHash : tokenHashes) {
            String sessionKey = sessionKey(tokenHash);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
                count++;
            }
            redisTemplate.delete(sessionKey);
        }
        redisTemplate.delete(indexKey);
        return count;
    }

    private String sessionKey(String tokenHash) {
        return KEY_PREFIX + tokenHash;
    }

    private String userIndexKey(Long userId) {
        return USER_INDEX_PREFIX + userId;
    }
}
