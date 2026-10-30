package com.studenthub.modules.rag.service;

import com.studenthub.modules.rag.config.RagSchedulerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIndexSchedulerTest {
    @Configuration
    @EnableScheduling
    static class Scheduling {}

    static class Timers {
        final CountDownLatch indexingStarted = new CountDownLatch(1);
        final CountDownLatch releaseIndexing = new CountDownLatch(1);
        final CountDownLatch realtimeRanDuringIndexing = new CountDownLatch(1);

        @Scheduled(fixedDelay = 60000, scheduler = "knowledgeIndexScheduler")
        public void slowIndex() throws InterruptedException {
            indexingStarted.countDown();
            releaseIndexing.await(10, TimeUnit.SECONDS);
        }

        @Scheduled(fixedDelay = 10)
        public void realtimePoll() {
            if (indexingStarted.getCount() == 0 && releaseIndexing.getCount() == 1) {
                realtimeRanDuringIndexing.countDown();
            }
        }
    }

    @Test void slowIndexDoesNotBlockUnqualifiedRealtimeAndPushTimers() throws Exception {
        var timers = new Timers();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(RagSchedulerConfiguration.class, Scheduling.class);
            context.registerBean(Timers.class, () -> timers);
            context.refresh();
            try {
                assertTrue(timers.indexingStarted.await(5, TimeUnit.SECONDS));
                assertTrue(timers.realtimeRanDuringIndexing.await(5, TimeUnit.SECONDS));
            } finally {
                timers.releaseIndexing.countDown();
            }
        }
    }
}
