package com.studenthub.modules.aichat.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.aichat.entity.AiMessageEntity;
import com.studenthub.modules.aichat.mapper.AiMessageMapper;
import com.studenthub.modules.aichat.security.AiSecurityRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import com.studenthub.modules.systemconfig.service.ScheduledTaskStateService;
import java.util.List;

/**
 * AI 历史内容保留策略执行器。
 *
 * <p>仅当 {@code ai_security_config.retentionEnabled=true} 且 days&gt;0 时执行；
 * 采用逻辑删除（BaseEntity.deleted）并限制单轮条数，避免长事务与误删；
 * 默认配置为关闭，因此不会影响现有数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiContentRetentionJob {

    private static final int BATCH_LIMIT = 500;

    private final AiMessageMapper messageMapper;
    private final ScheduledTaskStateService scheduledTaskStateService;

    @Scheduled(cron = "${studenthub.ai.retention-cron:0 30 3 * * *}")
    public void purgeExpiredMessages() {
        if (!scheduledTaskStateService.isEnabled(AiContentRetentionJob.class, "purgeExpiredMessages")) {
            return;
        }
        AiSecurityRuntime.Snapshot policy = AiSecurityRuntime.current();
        if (!policy.retentionEnabled() || policy.contentRetentionDays() <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.contentRetentionDays());
        try {
            // 先按时间取一批 ID，再按 ID 逻辑删除：避免 UPDATE ... LIMIT 在 H2/MySQL 上的方言差异。
            List<Long> ids = messageMapper.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                            .select(AiMessageEntity::getId)
                            .lt(AiMessageEntity::getCreatedAt, cutoff)
                            .last("LIMIT " + BATCH_LIMIT))
                    .stream().map(AiMessageEntity::getId).toList();
            if (ids.isEmpty()) {
                return;
            }
            int deleted = messageMapper.deleteByIds(ids);
            log.info("AI 历史内容清理完成, cutoff={}, deleted={}", cutoff, deleted);
        } catch (RuntimeException exception) {
            log.error("AI 历史内容清理失败, cutoff={}", cutoff, exception);
        }
    }
}
