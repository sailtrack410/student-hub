package com.studenthub.modules.schedule.job;

import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import com.studenthub.modules.schedule.config.ScheduleSyncProperties;
import com.studenthub.modules.schedule.service.impl.ScheduleAdjustmentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 课表定时同步入口：先采集调课通知（全校一条请求），再按班级分组逐班异步调度采样任务 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleSyncTimer {

    private final JwBindingMapper jwBindingMapper;
    private final ScheduleSyncWorker worker;
    private final ScheduleSyncProperties properties;
    private final ScheduleAdjustmentSyncService adjustmentSyncService;

    @Scheduled(cron = "${studenthub.schedule-sync.cron:0 0 7-21/2 * * *}")
    public void run() {
        if (!properties.isEnabled()) {
            return;
        }
        List<JwBindingEntity> students = jwBindingMapper.selectAllAuthenticatedStudents();
        if (students.isEmpty()) {
            log.info("课表同步：无有效学生绑定，跳过");
            return;
        }
        // 调课公告采集失败不阻断班级采样（内部已兜底吞异常）
        try {
            adjustmentSyncService.syncOnce();
        } catch (Exception exception) {
            log.warn("调课通知采集异常, msg={}", exception.getMessage());
        }
        Map<String, List<JwBindingEntity>> byClass = students.stream()
                .filter(binding -> binding.getClassName() != null && !binding.getClassName().isBlank())
                .collect(Collectors.groupingBy(JwBindingEntity::getClassName));
        log.info("课表同步开始, 班级数={}", byClass.size());
        byClass.forEach((className, bindings) -> worker.syncClassAsync(className, bindings));
    }
}
