package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DeliveryOutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final List<DeliveryOutboxHandler> handlers;
    private final TransactionTemplate isolated;
    private final TransactionTemplate nested;
    private final int maxAttempts;
    private final int leaseSeconds;
    private final boolean automatic;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), work -> {
                Thread thread = new Thread(work, "delivery-outbox"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public DeliveryOutboxService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager,
                                 List<DeliveryOutboxHandler> handlers,
                                 @Value("${studenthub.delivery.max-attempts:5}") int maxAttempts,
                                 @Value("${studenthub.delivery.lease-seconds:120}") int leaseSeconds,
                                 @Value("${studenthub.delivery.automatic:true}") boolean automatic) {
        this.jdbc = jdbc; this.mapper = mapper; this.handlers = handlers;
        this.maxAttempts = Math.max(1, maxAttempts); this.leaseSeconds = Math.max(5, leaseSeconds);
        this.automatic = automatic;
        isolated = new TransactionTemplate(manager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        nested = new TransactionTemplate(manager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    public void enqueueRealtime(String eventKey, Long recipientId, String type, Object payload) {
        enqueue(eventKey, "REALTIME", recipientId, Map.of("type", type, "data", payload));
    }

    public void enqueue(String eventKey, String topic, Long recipientId, Object payload) {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 160) throw new IllegalArgumentException("投递事件标识不合法");
        String json;
        try { json = mapper.writeValueAsString(payload); }
        catch (Exception exception) { throw new IllegalStateException("投递事件序列化失败", exception); }
        try {
            nested.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO delivery_outbox(event_key, topic, recipient_id, payload_json)
                    VALUES(?,?,?,?)
                    """, eventKey, topic, recipientId, json));
        } catch (DuplicateKeyException exception) {
            var existing = jdbc.queryForMap("SELECT topic,recipient_id,payload_json FROM delivery_outbox WHERE event_key=?", eventKey);
            if (!topic.equals(existing.get("topic")) || !recipientId.equals(((Number) existing.get("recipient_id")).longValue())
                    || !json.equals(existing.get("payload_json"))) {
                throw new IllegalStateException("投递事件标识内容冲突: " + eventKey);
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { wake(); }
            });
        } else wake();
    }

    /** 回调只是唤醒信号；进程在提交后崩溃或线程池拒绝时，周期扫描会恢复事件。 */
    @Scheduled(fixedDelayString = "${studenthub.delivery.poll-millis:2000}", initialDelay = 5000)
    public void wake() {
        if (!automatic || !scheduled.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try { drainOnce(); }
                catch (RuntimeException exception) { log.error("投递队列扫描失败", exception); }
                finally { scheduled.set(false); }
            });
        } catch (RejectedExecutionException exception) { scheduled.set(false); }
    }

    /** 每批上限 50，跨实例通过条件更新抢占租约；无需数据库专有 SKIP LOCKED 语法。 */
    public int drainOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<String> ready = jdbc.queryForList("""
                SELECT event_key FROM delivery_outbox
                WHERE (status='PENDING' AND available_at<=?) OR (status='INFLIGHT' AND lease_until<=?)
                ORDER BY available_at,created_at LIMIT 50
                """, String.class, Timestamp.valueOf(now), Timestamp.valueOf(now));
        int delivered = 0;
        for (String key : ready) {
            String lease = UUID.randomUUID().toString();
            LocalDateTime claimedAt = LocalDateTime.now();
            Event event = isolated.execute(status -> {
                int changed = jdbc.update("""
                        UPDATE delivery_outbox SET status='INFLIGHT',attempts=attempts+1,lease_token=?,lease_until=?,updated_at=CURRENT_TIMESTAMP
                        WHERE event_key=? AND ((status='PENDING' AND available_at<=?) OR (status='INFLIGHT' AND lease_until<=?))
                        """, lease, Timestamp.valueOf(claimedAt.plusSeconds(leaseSeconds)), key, Timestamp.valueOf(claimedAt), Timestamp.valueOf(claimedAt));
                if (changed == 0) return null;
                return jdbc.queryForObject("SELECT topic,recipient_id,payload_json,attempts FROM delivery_outbox WHERE event_key=?",
                        (row, index) -> new Event(row.getString(1), row.getLong(2), row.getString(3), row.getInt(4)), key);
            });
            if (event == null) continue;
            try {
                if (event.attempts() > maxAttempts) throw new IllegalStateException("租约多次过期，已达到投递上限");
                var handler = handlers.stream().filter(item -> item.supports(event.topic())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("未配置投递消费者: " + event.topic()));
                handler.deliver(key, event.topic(), event.recipient(), mapper.readTree(event.json()));
                delivered += jdbc.update("""
                        UPDATE delivery_outbox SET status='DONE',lease_token=NULL,lease_until=NULL,last_error=NULL,updated_at=CURRENT_TIMESTAMP
                        WHERE event_key=? AND lease_token=? AND status='INFLIGHT'
                        """, key, lease);
            } catch (Exception exception) {
                String error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                if (error.length() > 1024) error = error.substring(0, 1024);
                boolean exhausted = event.attempts() >= maxAttempts;
                long delay = Math.min(300, 1L << Math.min(8, event.attempts()));
                jdbc.update("""
                        UPDATE delivery_outbox SET status=?,available_at=?,lease_token=NULL,lease_until=NULL,last_error=?,updated_at=CURRENT_TIMESTAMP
                        WHERE event_key=? AND lease_token=? AND status='INFLIGHT'
                        """, exhausted ? "DEAD" : "PENDING", Timestamp.valueOf(LocalDateTime.now().plusSeconds(delay)), error, key, lease);
                log.warn("事件投递失败, eventKey={}, attempt={}, terminal={}", key, event.attempts(), exhausted);
            }
        }
        return delivered;
    }

    public List<Map<String, Object>> recent(String status) {
        String fields = "event_key,topic,recipient_id,status,attempts,available_at,lease_until,last_error,created_at,updated_at";
        return status == null || status.isBlank()
                ? jdbc.queryForList("SELECT " + fields + " FROM delivery_outbox ORDER BY updated_at DESC LIMIT 100")
                : jdbc.queryForList("SELECT " + fields + " FROM delivery_outbox WHERE status=? ORDER BY updated_at DESC LIMIT 100", status);
    }

    public boolean retryDead(String eventKey) {
        boolean changed = jdbc.update("""
                UPDATE delivery_outbox SET status='PENDING',attempts=0,available_at=CURRENT_TIMESTAMP,last_error=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE event_key=? AND status='DEAD'
                """, eventKey) > 0;
        if (changed) wake();
        return changed;
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
    private record Event(String topic, Long recipient, String json, int attempts) { }
}
