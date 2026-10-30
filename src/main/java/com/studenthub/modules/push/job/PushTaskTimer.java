package com.studenthub.modules.push.job;

import com.studenthub.modules.push.service.PushTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import com.studenthub.modules.systemconfig.service.ScheduledTaskStateService;
import org.springframework.stereotype.Component;

/**
 * 定时推送任务扫描器：每 30 秒检查一次到点的 SCHEDULED 任务并发送。
 *
 * <p>使用固定延迟而非固定频率，避免上一轮发送未结束时堆叠执行；单轮最多处理 20 个任务。
 * 扫描失败只记录日志，不影响下一次调度。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushTaskTimer {

    private final PushTaskService pushTaskService;
    private final ScheduledTaskStateService scheduledTaskStateService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void dispatchScheduledTasks() {
        if (!scheduledTaskStateService.isEnabled(PushTaskTimer.class, "dispatchScheduledTasks")) {
            return;
        }
        try {
            int handled = pushTaskService.dispatchDueTasks();
            if (handled > 0) {
                log.info("定时推送任务已处理 {} 个", handled);
            }
        } catch (RuntimeException exception) {
            log.error("定时推送任务扫描失败", exception);
        }
    }
}
