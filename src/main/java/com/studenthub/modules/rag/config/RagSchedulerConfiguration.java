package com.studenthub.modules.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Keep slow document/provider work off the scheduler used by realtime and push timers. */
@Configuration
public class RagSchedulerConfiguration {
    // Explicit default name keeps unqualified @Scheduled methods on their own scheduler.
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("scheduled-");
    }

    @Bean("knowledgeIndexScheduler")
    public ThreadPoolTaskScheduler knowledgeIndexScheduler() {
        return scheduler("knowledge-index-");
    }

    private ThreadPoolTaskScheduler scheduler(String prefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
