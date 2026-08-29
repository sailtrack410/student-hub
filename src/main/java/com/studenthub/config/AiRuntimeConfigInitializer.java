package com.studenthub.config;

import com.studenthub.integration.ai.AiRuntimeConfig;
import com.studenthub.integration.ai.AiRuntimeSnapshot;
import com.studenthub.modules.aichat.service.impl.AiConfigServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成后，把数据库里的 AI 配置刷入进程内运行时快照：
 * DB 有配置则以 DB 为准（即时生效体系），无配置时保持环境变量默认值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("flywayInitializer")
public class AiRuntimeConfigInitializer implements ApplicationRunner {

    private final AiConfigServiceImpl aiConfigService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            aiConfigService.refreshRuntime();
            AiRuntimeSnapshot snapshot = AiRuntimeConfig.current();
            if (snapshot != null) {
                log.info("AI 运行时配置已加载, enabled={}, chatModel={}", snapshot.enabled(),
                        snapshot.chat() == null ? "未配置" : snapshot.chat().model());
            }
        } catch (Exception exception) {
            // 数据库尚未就绪或表缺失时保持环境变量默认值，不阻断启动
            log.warn("AI 运行时配置加载失败，保持环境变量默认值: {}", exception.getMessage());
        }
    }
}