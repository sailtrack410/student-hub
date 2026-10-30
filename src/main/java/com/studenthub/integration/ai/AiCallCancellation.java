package com.studenthub.integration.ai;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 把下游 SSE 取消传递到阻塞的 HTTP 请求和响应体；每次任务退出都会清除线程上下文。 */
public final class AiCallCancellation {
    private static final ThreadLocal<AiCallCancellation> CURRENT = new ThreadLocal<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Set<Runnable> handlers = ConcurrentHashMap.newKeySet();

    public void run(Runnable work) {
        CURRENT.set(this);
        try { check(); work.run(); }
        finally { CURRENT.remove(); }
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            handlers.forEach(AiCallCancellation::closeQuietly);
            handlers.clear();
        }
    }

    public boolean isCancelled() { return cancelled.get(); }

    public void check() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("AI 请求已取消");
        }
    }

    public static void checkCurrent() {
        AiCallCancellation current = CURRENT.get();
        if (current != null) current.check();
        else if (Thread.currentThread().isInterrupted()) throw new CancellationException("AI 请求已取消");
    }

    public static AutoCloseable onCancel(Runnable handler) {
        AiCallCancellation current = CURRENT.get();
        if (current == null) return () -> { };
        current.handlers.add(handler);
        if (current.cancelled.get() && current.handlers.remove(handler)) closeQuietly(handler);
        return () -> current.handlers.remove(handler);
    }

    private static void closeQuietly(Runnable action) {
        try { action.run(); } catch (RuntimeException ignored) { }
    }
}
