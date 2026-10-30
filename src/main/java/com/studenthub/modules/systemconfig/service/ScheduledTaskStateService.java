package com.studenthub.modules.systemconfig.service;

import com.studenthub.modules.systemconfig.entity.ScheduledTaskStateEntity;
import com.studenthub.modules.systemconfig.mapper.ScheduledTaskStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务启停状态服务。
 *
 * <p>任务默认启用；只有显式写入 {@code enabled=0} 的 key 才会被自动触发跳过。
 * 状态缓存以 key 为粒度，管理端切换后立即生效；读取失败按启用处理，避免误停生产任务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskStateService {

    private final ScheduledTaskStateMapper mapper;
    private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    public boolean isEnabled(Class<?> type, String methodName) {
        return isEnabled(type.getName() + "#" + methodName);
    }

    public boolean isEnabled(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return true;
        }
        Boolean cached = cache.get(taskKey);
        if (cached != null) {
            return cached;
        }
        try {
            ScheduledTaskStateEntity state = mapper.selectById(taskKey);
            boolean enabled = state == null || !Integer.valueOf(0).equals(state.getEnabled());
            cache.put(taskKey, enabled);
            return enabled;
        } catch (RuntimeException exception) {
            log.warn("读取定时任务启停状态失败，按启用处理, key={}, error={}", taskKey, exception.getMessage());
            return true;
        }
    }

    @Transactional
    public boolean setEnabled(String taskKey, boolean enabled, Long operatorId, String note) {
        if (taskKey == null || taskKey.isBlank()) {
            throw new IllegalArgumentException("任务 key 不能为空");
        }
        ScheduledTaskStateEntity state = mapper.selectById(taskKey);
        if (state == null) {
            state = new ScheduledTaskStateEntity();
            state.setTaskKey(taskKey);
            state.setEnabled(enabled ? 1 : 0);
            state.setNote(note);
            state.setUpdatedBy(operatorId);
            state.setUpdatedAt(LocalDateTime.now());
            mapper.insert(state);
        } else {
            state.setEnabled(enabled ? 1 : 0);
            state.setNote(note);
            state.setUpdatedBy(operatorId);
            state.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(state);
        }
        cache.put(taskKey, enabled);
        return enabled;
    }
}
