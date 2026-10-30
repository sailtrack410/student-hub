package com.studenthub.modules.aichat.execution;

import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiMessageResponse;
import com.studenthub.modules.aichat.controller.dto.SendMessageRequest;
import com.studenthub.modules.aichat.service.AiConversationService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AiConversationIdempotencyIntegrationTest {
    @Autowired AiConversationService service;
    @Autowired JdbcTemplate jdbc;

    private AuthenticatedUser user(long id) {
        return new AuthenticatedUser(id, 1L, null, "test", "test", "STUDENT", "APPROVED", List.of(), List.of(), null, null);
    }
    private long adminId() { return jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class); }

    @Test void normalThenStreamUsesOneUserMessageAndDoneSeesCommittedResult() {
        var user = user(adminId());
        var conversation = service.createConversation(user.userId(), "idempotency");
        var request = new SendMessageRequest("现在几点", null, null, UUID.randomUUID().toString());
        AiMessageResponse first = service.sendMessage(user, conversation.conversationId(), request);
        AtomicReference<AiMessageResponse> done = new AtomicReference<>();
        service.sendMessageStream(user, conversation.conversationId(), request,
                delta -> fail("Replay must not call the model or emit duplicate deltas"), response -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?", Integer.class, conversation.conversationId()));
                    assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM ai_chat_request WHERE client_request_id=?", String.class, request.clientRequestId()));
                    done.set(response);
                }, error -> fail(error.toString()));
        assertEquals(first, done.get());
    }

    @Test void streamThenNormalKeepsOriginalMessageAndConversationOwnership() {
        var user = user(adminId());
        var conversation = service.createConversation(user.userId(), "stream idempotency");
        var request = new SendMessageRequest("现在几点", null, null, UUID.randomUUID().toString());
        AtomicReference<AiMessageResponse> done = new AtomicReference<>();
        service.sendMessageStream(user, conversation.conversationId(), request, delta -> { }, done::set, error -> fail(error.toString()));
        var replay = service.sendMessage(user, conversation.conversationId(), request);
        assertEquals(done.get(), replay);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?", Integer.class, conversation.conversationId()));
        var unauthorized = assertThrows(BusinessException.class, () -> service.sendMessage(user(user.userId() + 987654321), conversation.conversationId(), request));
        assertEquals("AI_404_01", unauthorized.getCode());
    }

    @Test void streamCancellationRollsBackInsertedUserAndRetainsCancelledRequest() {
        var user = user(adminId());
        var conversation = service.createConversation(user.userId(), "cancel idempotency");
        var request = new SendMessageRequest("现在几点", null, null, UUID.randomUUID().toString());
        assertThrows(java.util.concurrent.CancellationException.class, () -> service.sendMessageStream(user, conversation.conversationId(), request,
                delta -> { throw new java.util.concurrent.CancellationException("disconnected"); }, response -> fail(), error -> fail()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?", Integer.class, conversation.conversationId()));
        var replay = assertThrows(BusinessException.class, () -> service.sendMessage(user, conversation.conversationId(), request));
        assertEquals("AI_409_04", replay.getCode());
    }
}
