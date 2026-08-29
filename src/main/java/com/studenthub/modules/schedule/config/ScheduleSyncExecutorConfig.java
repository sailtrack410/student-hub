package com.studenthub.modules.schedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 课表同步专用线程池：有限并发，避免同时把所有托管会话打到教务系统 */
@Configuration
public class ScheduleSyncExecutorConfig {

    @Bean("scheduleSyncExecutor")
    public Executor scheduleSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("schedule-sync-");
        executor.initialize();
        return executor;
    }
}