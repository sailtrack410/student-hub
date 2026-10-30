package com.studenthub.modules.aichat.execution;

import com.studenthub.integration.ai.AiCallCancellation;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.modules.aichat.service.context.ConversationSummaryService;
import com.studenthub.modules.aichat.service.execution.AiExecutionResources;
import com.studenthub.modules.aichat.service.execution.AiStreamSession;
import com.studenthub.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiExecutionResourcesTest {
    @Test void streamQueueRejectsBeyondConfiguredBound() throws Exception {
        var resources = new AiExecutionResources(1, 1, 1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            resources.executeStream(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            resources.executeStream(() -> { });
            assertThrows(RejectedExecutionException.class, () -> resources.executeStream(() -> { }));
        } finally { release.countDown(); resources.close(); }
    }

    @Test void timeoutClosesBlockingUpstreamAndRemovesQueuedTask() throws Exception {
        var resources = new AiExecutionResources(1, 1, 1);
        SseEmitter emitter = mock(SseEmitter.class);
        var session = new AiStreamSession(emitter, resources);
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(timeout.capture());
        CountDownLatch entered = new CountDownLatch(1), exited = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();
        session.execute(() -> {
            try (var hook = AiCallCancellation.onCancel(() -> closed.set(true))) {
                entered.countDown();
                try { new CountDownLatch(1).await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            } catch (Exception ignored) { }
            finally { exited.countDown(); }
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        timeout.getValue().run();
        assertTrue(exited.await(2, TimeUnit.SECONDS));
        assertTrue(closed.get());
        resources.close();
    }

    @Test void sendFailureCancelsWorkInsteadOfContinuingToDone() throws Exception {
        var resources = new AiExecutionResources(1, 1, 1);
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        var session = new AiStreamSession(emitter, resources);
        AtomicBoolean continued = new AtomicBoolean();
        CountDownLatch exited = new CountDownLatch(1);
        try {
            session.execute(() -> {
                try { session.send("delta", "hello"); continued.set(true); }
                finally { exited.countDown(); }
            });
            assertTrue(exited.await(2, TimeUnit.SECONDS));
            assertFalse(continued.get());
            verify(emitter, never()).complete();
        } finally { resources.close(); }
    }

    @Test void containerShutdownCancelsUpstreamHooks() throws Exception {
        var resources = new AiExecutionResources(1, 1, 1);
        CountDownLatch entered = new CountDownLatch(1), closed = new CountDownLatch(1);
        new AiStreamSession(mock(SseEmitter.class), resources).execute(() -> {
            try (var hook = AiCallCancellation.onCancel(closed::countDown)) {
                entered.countDown();
                try { new CountDownLatch(1).await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            } catch (Exception ignored) { }
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        resources.close();
        assertTrue(closed.await(2, TimeUnit.SECONDS));
    }

    @Test void fullSummaryQueueDoesNotFailAlreadySavedAnswer() throws Exception {
        var resources = new AiExecutionResources(1, 1, 1);
        AiConversationMapper conversations = mock(AiConversationMapper.class);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(conversations.selectById(1L)).thenAnswer(invocation -> {
            entered.countDown(); release.await(2, TimeUnit.SECONDS); return null;
        });
        var summary = new ConversationSummaryService(conversations, mock(AiMessageMapper.class), mock(AiChatService.class), resources);
        try {
            summary.summarizeAsync(1L, 10L);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            summary.summarizeAsync(2L, 10L);
            assertDoesNotThrow(() -> summary.summarizeAsync(3L, 10L));
        } finally { release.countDown(); resources.close(); }
    }
}
