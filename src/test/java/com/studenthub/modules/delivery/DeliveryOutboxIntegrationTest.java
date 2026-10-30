package com.studenthub.modules.delivery;

import com.studenthub.modules.community.controller.dto.SendDirectMessageRequest;
import com.studenthub.modules.community.service.DirectMessageService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.entity.NotificationEntity;
import com.studenthub.modules.notification.service.NotificationPushDispatcher;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "studenthub.delivery.automatic=false")
@ActiveProfiles("test")
class DeliveryOutboxIntegrationTest {
    @Autowired NotificationService notifications;
    @Autowired DirectMessageService messages;
    @Autowired DeliveryOutboxService outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockBean NotificationPushDispatcher dispatcher;
    @MockBean RealtimeDeliveryGateway gateway;

    @BeforeEach void clearEvents() { jdbc.update("DELETE FROM delivery_outbox"); }
    private long admin() { return jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class); }
    private String unique() { return "outbox-" + UUID.randomUUID(); }
    private NotificationSendCommand notification(String title) { return new NotificationSendCommand(admin(), "SYSTEM", title, "test content", null, null); }
    private TransactionTemplate transaction() { return new TransactionTemplate(manager); }
    private AuthenticatedUser sender() {
        long id = admin();
        long campus = jdbc.queryForObject("SELECT campus_id FROM sys_user WHERE id=?", Long.class, id);
        return new AuthenticatedUser(id, campus, null, "admin", "test", "STUDENT", "APPROVED", List.of(), List.of(), null, null);
    }
    private long createPeer(AuthenticatedUser sender) {
        long id = 8_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        jdbc.update("""
                INSERT INTO sys_user(id,campus_id,username,display_name,status,created_at,updated_at,created_by,updated_by)
                VALUES(?,?,?,?,'ENABLED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)
                """, id, sender.campusId(), unique(), "outbox peer", sender.userId(), sender.userId());
        return id;
    }

    @Test void notificationAndOutboxRollbackTogetherThenRetryUsesOneSavedNotification() {
        String rolledBack = unique();
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(tx -> {
            notifications.send(notification(rolledBack));
            verifyNoInteractions(dispatcher);
            throw new IllegalStateException("business failed after notification");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE title=?", Integer.class, rolledBack));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_outbox", Integer.class));

        String title = unique();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            NotificationEntity entity = invocation.getArgument(0);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id=?", Integer.class, entity.getId()));
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("transport failure");
            return null;
        }).when(dispatcher).dispatch(any());
        notifications.send(notification(title));
        verifyNoInteractions(dispatcher);
        assertEquals(0, outbox.drainOnce());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM delivery_outbox", String.class));
        jdbc.update("UPDATE delivery_outbox SET available_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertEquals(1, outbox.drainOnce());
        verify(dispatcher, times(2)).dispatch(any());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE title=?", Integer.class, title));
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM delivery_outbox", String.class));
    }

    @Test void directMessageRollbackPreventsDeliveryAndCommittedRetryKeepsOneMessage() {
        var sender = sender(); long peer = createPeer(sender);
        String rolledBack = unique();
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(tx -> {
            messages.send(sender, new SendDirectMessageRequest(peer, rolledBack, null, null, null));
            verifyNoInteractions(gateway);
            throw new IllegalStateException("business failed after message");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM community_direct_message WHERE content=?", Integer.class, rolledBack));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_outbox", Integer.class));

        String content = unique();
        var saved = messages.send(sender, new SendDirectMessageRequest(peer, content, null, null, null));
        verifyNoInteractions(gateway);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM community_direct_message WHERE id=?", Integer.class, saved.messageId()));
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("socket unavailable");
            return null;
        }).when(gateway).dispatch(eq(peer), eq("DIRECT_MESSAGE"), any(), eq("direct-message:" + saved.messageId()));
        assertEquals(0, outbox.drainOnce());
        jdbc.update("UPDATE delivery_outbox SET available_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertEquals(1, outbox.drainOnce());
        verify(gateway, times(2)).dispatch(eq(peer), eq("DIRECT_MESSAGE"), any(), eq("direct-message:" + saved.messageId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM community_direct_message WHERE content=?", Integer.class, content));
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM delivery_outbox", String.class));
    }
}
