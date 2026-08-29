package com.studenthub.integration.ai;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 进程内 AI 运行时配置持有器：provider 与 chat service 每次调用读取当前快照，
 * 管理后台保存配置后调用 {@link #update} 使其即时生效，无需重启。
 */
public final class AiRuntimeConfig {

    private static final AtomicReference<AiRuntimeSnapshot> CURRENT = new AtomicReference<>();

    private AiRuntimeConfig() {
    }

    public static AiRuntimeSnapshot current() {
        return CURRENT.get();
    }

    public static void update(AiRuntimeSnapshot snapshot) {
        CURRENT.set(snapshot);
    }
}