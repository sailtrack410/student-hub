package com.studenthub.modules.systemconfig.service;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.systemconfig.controller.dto.ScheduledTaskResponse;
import com.studenthub.modules.systemconfig.controller.dto.ScheduledTaskRunResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 定时任务注册表：从 Spring 的 ScheduledTaskHolder 实时读取 @Scheduled 任务，
 * 支持手动执行一次并记录最近一次手动执行结果。
 *
 * <p>说明：自动触发由 Spring 调度器负责；本类只观测“已注册任务”，不修改其他模块的任务。
 * 最近执行记录仅覆盖本页发起的手动执行（自动执行需各任务自行埋点，本期不侵入其他 bundle）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskRegistry {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "ScheduleSyncTimer", "教务课表定时同步",
            "OfficialFeedSyncTimer", "官网抓取定时同步",
            "NoticePublishTimer", "定时公告发布");

    private final List<ScheduledTaskHolder> holders;
    private final Environment environment;
    private final ScheduledTaskStateService scheduledTaskStateService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "admin-scheduled-task-manual");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ScheduledTask> taskIndex = new ConcurrentHashMap<>();
    private final Map<String, RunRecord> lastRuns = new ConcurrentHashMap<>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public List<ScheduledTaskResponse> tasks() {
        refreshIndex();
        List<ScheduledTaskResponse> out = new ArrayList<>();
        for (Map.Entry<String, ScheduledTask> entry : taskIndex.entrySet()) {
            ScheduledTask task = entry.getValue();
            Method method = methodOf(task);
            if (method == null) {
                method = methodForKey(entry.getKey());
            }
            RunRecord run = lastRuns.get(entry.getKey());
            out.add(new ScheduledTaskResponse(entry.getKey(),
                    method == null ? "-" : method.getDeclaringClass().getName(),
                    method == null ? "-" : method.getName(),
                    triggerOf(method),
                    descriptionOf(method),
                    scheduledTaskStateService.isEnabled(entry.getKey()),
                    running.contains(entry.getKey()),
                    run == null ? null : run.status(),
                    run == null ? null : run.startedAt(),
                    run == null ? null : run.finishedAt(),
                    run == null ? null : run.durationMs(),
                    run == null ? null : run.error()));
        }
        out.sort(Comparator.comparing(ScheduledTaskResponse::key));
        return out;
    }

    public ScheduledTaskRunResponse run(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "任务 key 不能为空");
        }
        refreshIndex();
        ScheduledTask task = taskIndex.get(key);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在或尚未注册：" + key);
        }
        if (!running.add(key)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该任务正在执行，请稍后再试");
        }
        String startedAt = LocalDateTime.now().format(TS);
        long startMillis = System.currentTimeMillis();
        RunRecord run = new RunRecord(key, "RUNNING", startedAt, null, null, null);
        lastRuns.put(key, run);
        executor.submit(() -> {
            try {
                task.getTask().getRunnable().run();
                lastRuns.put(key, new RunRecord(key, "SUCCESS", startedAt,
                        LocalDateTime.now().format(TS), System.currentTimeMillis() - startMillis, null));
                log.info("手动执行定时任务成功, key={}, durationMs={}", key, System.currentTimeMillis() - startMillis);
            } catch (Throwable throwable) {
                String message = throwable.getMessage() == null
                        ? throwable.getClass().getSimpleName() : throwable.getMessage();
                if (message.length() > 300) {
                    message = message.substring(0, 300);
                }
                lastRuns.put(key, new RunRecord(key, "FAILED", startedAt,
                        LocalDateTime.now().format(TS), System.currentTimeMillis() - startMillis, message));
                log.warn("手动执行定时任务失败, key={}", key, throwable);
            } finally {
                running.remove(key);
            }
        });
        return new ScheduledTaskRunResponse(key, run.status(), run.startedAt(), null, null, null);
    }

    /** 供健康检查使用：最多返回前 200 个已注册任务数，避免异常时拖慢健康接口。 */
    public int taskCount() {
        refreshIndex();
        return taskIndex.size();
    }

    private void refreshIndex() {
        try {
            for (ScheduledTaskHolder holder : holders) {
                Set<ScheduledTask> tasks = holder.getScheduledTasks();
                if (tasks == null) {
                    continue;
                }
                for (ScheduledTask task : tasks) {
                    Method method = methodOf(task);
                    String key = keyOf(method, task);
                    if (key != null) {
                        taskIndex.put(key, task);
                    }
                }
            }
        } catch (Exception exception) {
            log.debug("读取定时任务列表失败: {}", exception.getMessage());
        }
    }

    private Method methodOf(ScheduledTask task) {
        try {
            if (task.getTask().getRunnable() instanceof ScheduledMethodRunnable runnable) {
                return runnable.getMethod();
            }
        } catch (Exception ignored) {
            // 非方法型任务（如自定义 Runnable）不暴露方法信息
        }
        return null;
    }

    /** 兼容 Spring 对 Runnable 的包装：从 task key（类名#方法名）反解方法，保证触发规则/说明可读。 */
    private Method methodForKey(String key) {
        if (key == null) {
            return null;
        }
        int hash = key.indexOf('#');
        if (hash <= 0 || hash >= key.length() - 1) {
            return null;
        }
        try {
            Class<?> type = Class.forName(key.substring(0, hash));
            return type.getDeclaredMethod(key.substring(hash + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String keyOf(Method method, ScheduledTask task) {
        if (method != null) {
            return method.getDeclaringClass().getName() + "#" + method.getName();
        }
        String text = String.valueOf(task.getTask().getRunnable());
        int dot = text.lastIndexOf('.');
        if (dot > 0 && dot < text.length() - 1) {
            return text.substring(0, dot) + "#" + text.substring(dot + 1);
        }
        return text;
    }

    private String triggerOf(Method method) {
        if (method == null) {
            return "—";
        }
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        if (scheduled == null) {
            return "—";
        }
        if (!scheduled.cron().isBlank()) {
            return "cron：" + environment.resolvePlaceholders(scheduled.cron());
        }
        if (!scheduled.fixedDelayString().isBlank()) {
            return "fixedDelay：" + environment.resolvePlaceholders(scheduled.fixedDelayString()) + " ms";
        }
        if (scheduled.fixedDelay() > 0) {
            return "fixedDelay：" + scheduled.fixedDelay() + " ms";
        }
        if (!scheduled.fixedRateString().isBlank()) {
            return "fixedRate：" + environment.resolvePlaceholders(scheduled.fixedRateString()) + " ms";
        }
        if (scheduled.fixedRate() > 0) {
            return "fixedRate：" + scheduled.fixedRate() + " ms";
        }
        return "—";
    }

    private String descriptionOf(Method method) {
        if (method == null) {
            return "后台定时任务";
        }
        String simple = method.getDeclaringClass().getSimpleName();
        return DESCRIPTIONS.getOrDefault(simple, "后台定时任务 · " + simple);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record RunRecord(String key, String status, String startedAt, String finishedAt,
                            Long durationMs, String error) {
    }
}
