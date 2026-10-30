package com.studenthub.modules.aichat.service.execution;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 应用和管理端共用有界流式池；摘要使用独立有界串行池。容器关闭时取消排队和正在运行的任务。 */
@Component
public class AiExecutionResources {
    private final java.util.Set<Runnable> streamTasks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor streams;
    private final ThreadPoolExecutor summaries;

    public AiExecutionResources(@Value("${studenthub.ai.execution.stream-threads:4}") int threads,
                                @Value("${studenthub.ai.execution.stream-queue-capacity:16}") int streamQueue,
                                @Value("${studenthub.ai.execution.summary-queue-capacity:32}") int summaryQueue) {
        streams = pool(Math.max(1, threads), Math.max(1, streamQueue), "ai-stream-");
        summaries = pool(1, Math.max(1, summaryQueue), "ai-summary-");
    }

    private static ThreadPoolExecutor pool(int threads, int capacity, String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void executeStream(Runnable task) {
        streamTasks.add(task);
        try { streams.execute(task); }
        catch (RuntimeException exception) { streamTasks.remove(task); throw exception; }
    }
    public void removeStream(Runnable task) { streams.remove(task); streamTasks.remove(task); }
    public void executeSummary(Runnable task) { summaries.execute(task); }

    @PreDestroy
    public void close() {
        streamTasks.forEach(task -> { if (task instanceof Future<?> future) future.cancel(true); });
        for (Runnable task : streams.shutdownNow()) {
            if (task instanceof Future<?> future) future.cancel(true);
        }
        for (Runnable task : summaries.shutdownNow()) {
            if (task instanceof Future<?> future) future.cancel(true);
        }
    }
}
