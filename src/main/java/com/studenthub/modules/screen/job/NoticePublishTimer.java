package com.studenthub.modules.screen.job;

import com.studenthub.modules.screen.service.ScreenAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时发布扫描：把到点的 SCHEDULED 公告置为 PUBLISHED（30 秒粒度，分钟级发布精度足够） */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticePublishTimer {

    private final ScreenAdminService screenAdminService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void run() {
        try {
            int published = screenAdminService.publishDueScheduled();
            if (published > 0) {
                log.info("定时公告发布完成, count={}", published);
            }
        } catch (Exception exception) {
            log.warn("定时公告发布扫描失败", exception);
        }
    }
}
