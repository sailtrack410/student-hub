package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryOutboxServiceTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private TransactionTemplate transaction;
    private DeliveryOutboxService outbox;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V94__delivery_outbox.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE test_business(id BIGINT PRIMARY KEY, content VARCHAR(100))");
        manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        outbox = service((key, recipient, payload) -> calls.incrementAndGet(), false);
    }
    @AfterEach void close() { outbox.close(); }

    @FunctionalInterface interface Delivery { void run(String key, Long recipient, JsonNode payload); }
    private DeliveryOutboxService service(Delivery delivery, boolean automatic) {
        DeliveryOutboxHandler handler = new DeliveryOutboxHandler() {
            public boolean supports(String topic) { return "TEST".equals(topic); }
            public void deliver(String key, String topic, Long recipient, JsonNode payload) { delivery.run(key, recipient, payload); }
        };
        return new DeliveryOutboxService(jdbc, new ObjectMapper(), manager, List.of(handler), 2, 5, automatic);
    }
    private void enqueue() { outbox.enqueue("event-1", "TEST", 7L, Map.of("businessId", 1)); }
    private String status() { return jdbc.queryForObject("SELECT status FROM delivery_outbox WHERE event_key='event-1'", String.class); }
    private int attempts() { return jdbc.queryForObject("SELECT attempts FROM delivery_outbox WHERE event_key='event-1'", Integer.class); }
    private void makeReady() { jdbc.update("UPDATE delivery_outbox SET available_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)"); }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    @Test void originalTransactionRollbackRemovesBusinessAndEvent() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO test_business VALUES(1,'message')");
            enqueue();
            throw new IllegalStateException("original transaction rolled back");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_business", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_outbox", Integer.class));
        assertEquals(0, outbox.drainOnce());
        assertEquals(0, calls.get());
    }

    @Test void automaticDeliveryStartsOnlyAfterOriginalTransactionCommits() {
        outbox.close();
        CountDownLatch delivered = new CountDownLatch(1);
        outbox = service((key, recipient, payload) -> {
            assertEquals("message", jdbc.queryForObject("SELECT content FROM test_business WHERE id=1", String.class));
            delivered.countDown();
        }, true);
        transaction.executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO test_business VALUES(1,'message')");
            enqueue();
            assertEquals(1, delivered.getCount());
        });
        await(delivered);
    }

    @Test void duplicateKeyUsesSavepointAndDoesNotPoisonOriginalTransaction() {
        transaction.executeWithoutResult(tx -> {
            enqueue(); enqueue();
            jdbc.update("INSERT INTO test_business VALUES(1,'message')");
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_outbox", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_business", Integer.class));
        assertThrows(IllegalStateException.class, () -> outbox.enqueue("event-1", "TEST", 8L, Map.of("businessId", 1)));
        assertEquals(1, outbox.drainOnce());
        assertEquals(0, outbox.drainOnce());
        assertEquals(1, calls.get());
        assertEquals("DONE", status());
    }

    @Test void failuresBackOffThenBecomeVisibleAndAdminRetryReusesSameEvent() {
        outbox.close();
        outbox = service((key, recipient, payload) -> {
            if (calls.incrementAndGet() <= 2) throw new IllegalStateException("provider unavailable");
        }, false);
        transaction.executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO test_business VALUES(1,'message')"); enqueue();
        });
        assertEquals(0, outbox.drainOnce());
        assertEquals("PENDING", status()); assertEquals(1, attempts());
        assertEquals(0, outbox.drainOnce()); assertEquals(1, calls.get());
        makeReady(); outbox.drainOnce();
        assertEquals("DEAD", status()); assertEquals(2, attempts());
        var dead = outbox.recent("DEAD");
        assertEquals(1, dead.size());
        assertEquals("provider unavailable", dead.get(0).get("last_error"));
        assertFalse(dead.get(0).containsKey("payload_json"));
        assertTrue(outbox.retryDead("event-1"));
        assertEquals(0, attempts()); assertEquals(1, outbox.drainOnce());
        assertEquals("DONE", status()); assertFalse(outbox.retryDead("event-1"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_business", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_outbox", Integer.class));
    }

    @Test void concurrentWorkersOnlyOneClaimsLiveLease() throws Exception {
        outbox.close();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        outbox = service((key, recipient, payload) -> { calls.incrementAndGet(); entered.countDown(); await(release); }, false);
        var worker = Executors.newSingleThreadExecutor();
        try {
            enqueue();
            var first = worker.submit(outbox::drainOnce);
            await(entered);
            assertEquals(0, outbox.drainOnce());
            assertEquals(1, calls.get());
            release.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
            assertEquals("DONE", status());
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test void expiredLeaseIsRecoveredAfterWorkerCrash() {
        enqueue();
        jdbc.update("UPDATE delivery_outbox SET status='INFLIGHT',attempts=1,lease_token='old-worker',lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertEquals(1, outbox.drainOnce());
        assertEquals("DONE", status()); assertEquals(2, attempts()); assertEquals(1, calls.get());
    }

    @Test void staleOwnerCannotAcknowledgeOrEraseNewWorkersLease() throws Exception {
        outbox.close();
        CountDownLatch firstEntered = new CountDownLatch(1), firstRelease = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1), secondRelease = new CountDownLatch(1);
        outbox = service((key, recipient, payload) -> {
            if (calls.incrementAndGet() == 1) { firstEntered.countDown(); await(firstRelease); }
            else { secondEntered.countDown(); await(secondRelease); }
        }, false);
        var workers = Executors.newFixedThreadPool(2);
        try {
            enqueue(); var first = workers.submit(outbox::drainOnce); await(firstEntered);
            jdbc.update("UPDATE delivery_outbox SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
            var second = workers.submit(outbox::drainOnce); await(secondEntered);
            String lease = jdbc.queryForObject("SELECT lease_token FROM delivery_outbox", String.class);
            firstRelease.countDown(); assertEquals(0, first.get(5, TimeUnit.SECONDS));
            assertEquals("INFLIGHT", status());
            assertEquals(lease, jdbc.queryForObject("SELECT lease_token FROM delivery_outbox", String.class));
            secondRelease.countDown(); assertEquals(1, second.get(5, TimeUnit.SECONDS));
            assertEquals("DONE", status());
        } finally { firstRelease.countDown(); secondRelease.countDown(); workers.shutdownNow(); }
    }

    @Test void repeatedLeaseExpiryTerminatesWithoutCallingProviderAgain() {
        enqueue();
        jdbc.update("UPDATE delivery_outbox SET status='INFLIGHT',attempts=2,lease_token='old-worker',lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertEquals(0, outbox.drainOnce());
        assertEquals("DEAD", status()); assertEquals(0, calls.get());
    }
}
