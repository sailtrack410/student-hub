package com.studenthub.modules.aichat.service.execution;

import com.studenthub.common.ErrorCode;
import com.studenthub.integration.ai.AiCallCancellation;
import com.studenthub.modules.aichat.controller.dto.AiStreamError;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** 每个连接拥有一个可取消任务；主动完成与客户端断开分开处理。 */
public final class AiStreamSession {
    private final SseEmitter emitter;
    private final AiExecutionResources resources;
    private final AiCallCancellation cancellation = new AiCallCancellation();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private volatile FutureTask<Void> task;

    public AiStreamSession(SseEmitter emitter, AiExecutionResources resources) {
        this.emitter = emitter;
        this.resources = resources;
        emitter.onTimeout(this::cancel);
        emitter.onError(error -> cancel());
        emitter.onCompletion(() -> { if (!terminal.get()) cancel(); });
    }

    public void execute(Runnable work) {
        FutureTask<Void> next = new FutureTask<>(() -> {
            try { cancellation.run(work); }
            catch (CancellationException ignored) { cancel(); }
            catch (RuntimeException exception) {
                if (!cancellation.isCancelled()) complete("error", new AiStreamError(
                        ErrorCode.AI_PROVIDER_ERROR.getCode(), "AI 调用失败，请发起新问题"));
            }
            return null;
        }) {
            @Override protected void done() {
                if (isCancelled()) cancellation.cancel();
                resources.removeStream(this);
            }
        };
        task = next;
        if (cancellation.isCancelled()) { next.cancel(true); return; }
        try { resources.executeStream(next); }
        catch (RejectedExecutionException exception) {
            next.cancel(false);
            complete("error", new AiStreamError(ErrorCode.AI_BUSY.getCode(), ErrorCode.AI_BUSY.getMessage()));
        }
    }

    public void send(String event, Object data) {
        cancellation.check();
        try { emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data)); }
        catch (Exception exception) {
            cancel();
            throw new CancellationException("客户端连接已结束");
        }
    }

    public void complete(String event, Object data) {
        if (terminal.get() || cancellation.isCancelled()) return;
        send(event, data);
        terminal.set(true);
        emitter.complete();
    }

    public void cancel() {
        cancellation.cancel();
        FutureTask<Void> current = task;
        if (current != null) {
            current.cancel(true);
            resources.removeStream(current);
        }
    }
}
