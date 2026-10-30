package com.studenthub.modules.officialfeed.job;

import com.studenthub.modules.officialfeed.service.OfficialFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import com.studenthub.modules.systemconfig.service.ScheduledTaskStateService;
import org.springframework.stereotype.Component;

/** 官方通知定时抓取：默认首次延迟 3 分钟，之后每 30 分钟一次；也可在管理端手动触发。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialFeedSyncTimer {

    private final OfficialFeedService officialFeedService;
    private final ScheduledTaskStateService scheduledTaskStateService;

    @Scheduled(fixedDelayString = "${studenthub.official-feed.interval-ms:1800000}",
            initialDelayString = "${studenthub.official-feed.initial-delay-ms:180000}")
    public void run() {
        if (!scheduledTaskStateService.isEnabled(OfficialFeedSyncTimer.class, "run")) {
            return;
        }
        try {
            var results = officialFeedService.syncAllEnabled();
            if (!results.isEmpty()) {
                log.info("官方通知定时同步完成, sources={}", results.size());
            }
        } catch (Exception exception) {
            log.warn("官方通知定时同步失败: {}", exception.getMessage());
        }
        try {
            int published = officialFeedService.publishDueScheduledArticles();
            if (published > 0) {
                log.info("官方通知定时发布完成, count={}", published);
            }
        } catch (Exception exception) {
            log.warn("官方通知定时发布失败: {}", exception.getMessage());
        }
    }
}
