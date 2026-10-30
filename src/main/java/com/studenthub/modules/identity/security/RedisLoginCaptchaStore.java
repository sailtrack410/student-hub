package com.studenthub.modules.identity.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

/** 与登录会话使用同一 Redis；Lua 保证跨节点校验一次即消费，兼容 Redis 6。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "redis")
public class RedisLoginCaptchaStore implements LoginCaptchaStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
        "local value=redis.call('GET',KEYS[1]); redis.call('DEL',KEYS[1]); return value", String.class);
    @Override
    public void put(String id, String answer, Duration ttl) { redis.opsForValue().set(key(id), answer, ttl); }
    @Override
    public String consume(String id) { return redis.execute(CONSUME, List.of(key(id))); }
    private String key(String id) { return "auth:login-captcha:" + id; }
}
