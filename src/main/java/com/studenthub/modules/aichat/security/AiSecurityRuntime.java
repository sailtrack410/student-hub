package com.studenthub.modules.aichat.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 安全策略运行时快照：与 ai_security_config 表一一对应，管理端保存后即时刷新。
 *
 * <p>缺省值刻意保持「宽松但不失效」：开启策略、敏感词为空、限流 30/分、配额 200/日、
 * 保留 90 天但关闭自动清理 —— 与改造前的行为兼容，不会因为上线策略而误伤正常用户。</p>
 */
public final class AiSecurityRuntime {

    /** 策略快照。 */
    public record Snapshot(
            boolean enabled,
            List<String> sensitiveWords,
            List<String> toolWhitelist,
            int rateLimitPerMinute,
            int dailyQuotaPerUser,
            int contentRetentionDays,
            boolean retentionEnabled,
            boolean blockOnHit) {
    }

    private static final Snapshot DEFAULT = new Snapshot(
            true, List.of(), List.of(), 30, 200, 90, false, true);

    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(DEFAULT);

    private AiSecurityRuntime() {
    }

    public static Snapshot current() {
        Snapshot snapshot = CURRENT.get();
        return snapshot == null ? DEFAULT : snapshot;
    }

    public static void update(Snapshot snapshot) {
        CURRENT.set(snapshot == null ? DEFAULT : snapshot);
    }

    public static void reset() {
        CURRENT.set(DEFAULT);
    }
}
