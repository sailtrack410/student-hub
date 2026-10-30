package com.studenthub.modules.push.content.job;

import com.studenthub.modules.push.content.service.CreatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import com.studenthub.modules.systemconfig.service.ScheduledTaskStateService;
import org.springframework.stereotype.Component;

/**
 * 创作者处罚到期处理：每 5 分钟把 end_at 已过的 ACTIVE 处罚置为 EXPIRED，
 * 并重算对应创作者的账号状态（禁言到期自动恢复正常）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorPenaltyTimer {

    private final CreatorService creatorService;
    private final ScheduledTaskStateService scheduledTaskStateService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void expirePenalties() {
        if (!scheduledTaskStateService.isEnabled(CreatorPenaltyTimer.class, "expirePenalties")) {
            return;
        }
        try {
            int expired = creatorService.expireStalePenalties();
            if (expired > 0) {
                log.info("创作者处罚到期处理 {} 条", expired);
            }
        } catch (RuntimeException exception) {
            log.error("创作者处罚到期处理失败", exception);
        }
    }
}
