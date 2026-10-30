package com.studenthub.modules.community.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Redis pub/sub 传递实时唤醒；TTL presence 按节点租约，节点退出后自动过期。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="studenthub.security", name="session-store", havingValue="redis")
public class RedisRealtimeEventBus implements RealtimeEventBus {
    public static final String TOPIC = "studenthub:realtime:v1";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final String nodeId = UUID.randomUUID().toString();
    private final Set<Long> localUsers = ConcurrentHashMap.newKeySet();
    @Override
    public void publish(Long recipientId, String type, Object data) {
        try { redis.convertAndSend(TOPIC, mapper.writeValueAsString(new Delivery(recipientId,type,data))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("实时事件序列化失败",e); }
    }
    public void receive(String payload) {
        try { events.publishEvent(mapper.readValue(payload, Delivery.class)); }
        catch (Exception e) { log.warn("实时事件分发失败", e); }
    }
    @Override
    public void presence(Long userId, boolean online) {
        if (online) { localUsers.add(userId); renew(userId); }
        else { localUsers.remove(userId); redis.opsForZSet().remove(key(userId),nodeId); }
    }
    @Override
    public boolean isOnline(Long userId) {
        String key = key(userId);
        redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, System.currentTimeMillis());
        Long size = redis.opsForZSet().zCard(key);
        return size != null && size > 0;
    }
    @Scheduled(fixedDelay=30_000)
    public void heartbeat() {
        for (Long userId : localUsers) {
            try { renew(userId); } catch (RuntimeException e) { log.warn("实时在线租约续期失败 userId={}",userId,e); }
        }
    }
    private void renew(Long userId) {
        redis.opsForZSet().add(key(userId),nodeId,System.currentTimeMillis()+75_000);
        redis.expire(key(userId),Duration.ofSeconds(90));
    }
    private String key(Long userId) { return "studenthub:presence:"+userId; }
}
