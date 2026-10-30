package com.studenthub.modules.aichat.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import com.studenthub.modules.aichat.service.execution.AiRequestExecutionService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiRequestExecutionTest {
    private JdbcTemplate jdbc;
    private AiRequestExecutionService service;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V93__ai_chat_request_idempotency.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE test_message(id BIGINT PRIMARY KEY, content VARCHAR(200))");
        service = new AiRequestExecutionService(jdbc, new ObjectMapper().findAndRegisterModules(), new DataSourceTransactionManager(source));
    }

    private SendMessageRequest request(String content, String id) { return new SendMessageRequest(content, null, null, id); }
    private AiMessageResponse answer() {
        int id = calls.incrementAndGet();
        jdbc.update("INSERT INTO test_message(id,content) VALUES (?,?)", id, "reply");
        return new AiMessageResponse((long) id, "ASSISTANT", "reply", null, false, null, null, null, LocalDateTime.now());
    }
    private void assertCode(String code, Runnable work) {
        assertEquals(code, assertThrows(BusinessException.class, work::run).getCode());
    }

    @Test void repeatedRequestReplaysSameMessageAfterCommitWithoutSecondModelCall() {
        var first = service.execute(1L, 2L, request("hello", "normal-to-stream"), this::answer);
        var replay = service.execute(1L, 2L, request(" hello ", "normal-to-stream"), this::answer);
        assertEquals(first, replay);
        assertEquals(1, calls.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_message", Integer.class));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM ai_chat_request", String.class));
    }

    @Test void runningDuplicateAndConflictingPayloadNeverInvokeWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> service.execute(1L, 2L, request("hello", "concurrent"), () -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return answer();
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertCode("AI_409_01", () -> service.execute(1L, 2L, request("hello", "concurrent"), this::answer));
            assertCode("AI_409_02", () -> service.execute(1L, 2L, request("different", "concurrent"), this::answer));
            assertEquals(0, calls.get());
            release.countDown();
            assertNotNull(first.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void failedOperationRollsBackMessagesAndPersistsTerminalFailure() {
        assertThrows(IllegalStateException.class, () -> service.execute(1L, 2L, request("hello", "failure"), () -> {
            answer(); throw new IllegalStateException("provider disconnected");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_message", Integer.class));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM ai_chat_request", String.class));
        assertCode("AI_409_03", () -> service.execute(1L, 2L, request("hello", "failure"), this::answer));
        assertEquals(1, calls.get());
    }

    @Test void cancelledOperationRollsBackAndCannotRegenerateOnFallback() {
        assertThrows(CancellationException.class, () -> service.execute(1L, 2L, request("hello", "cancel"), () -> {
            answer(); throw new CancellationException("client disconnected");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_message", Integer.class));
        assertCode("AI_409_04", () -> service.execute(1L, 2L, request("hello", "cancel"), this::answer));
        assertEquals(1, calls.get());
    }

    @Test void distinctUsersAndConversationsHaveIndependentRequestKeys() {
        service.execute(1L, 2L, request("hello", "same"), this::answer);
        service.execute(2L, 2L, request("hello", "same"), this::answer);
        service.execute(1L, 3L, request("hello", "same"), this::answer);
        assertEquals(3, calls.get());
    }

    @Test void changedAttachmentCannotReuseRequestKey() {
        var first = new SendMessageRequest("hello", List.of(7L), null, "attachments");
        service.execute(1L, 2L, first, this::answer);
        assertCode("AI_409_02", () -> service.execute(1L, 2L,
                new SendMessageRequest("hello", null, List.of(new SendMessageRequest.Attachment("DOCUMENT", 7L)), "attachments"), this::answer));
        assertEquals(1, calls.get());
    }

    @Test void oldClientsRemainCompatibleAndFailedWorkStillRollsBack() {
        service.execute(1L, 2L, request("hello", null), this::answer);
        service.execute(1L, 2L, request("hello", null), this::answer);
        assertThrows(IllegalStateException.class, () -> service.execute(1L, 2L, request("hello", null), () -> {
            answer(); throw new IllegalStateException();
        }));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM test_message", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_chat_request", Integer.class));
    }
}
