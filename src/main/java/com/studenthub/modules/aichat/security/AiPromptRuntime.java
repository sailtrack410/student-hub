package com.studenthub.modules.aichat.security;

import com.studenthub.modules.aichat.service.CampusAssistantPrompts;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 提示词运行时持有器：进程内快照，避免每次对话都查库。
 *
 * <p>生效顺序：数据库 active 版本 → 代码内置基线 {@link CampusAssistantPrompts#SYSTEM_PROMPT}。
 * 管理端保存/激活/停用提示词后由 {@code AiPromptRuntimeService} 刷新本快照，
 * 因此无需重启即可生效；未配置数据库版本时行为与改造前完全一致。</p>
 */
public final class AiPromptRuntime {

    /** 运行时快照。 */
    public record Snapshot(String promptCode, int versionNo, String systemPrompt) {
    }

    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>();

    private AiPromptRuntime() {
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    /** 当前生效系统提示词（数据库版本优先，缺省代码基线）。 */
    public static String systemPrompt() {
        Snapshot snapshot = CURRENT.get();
        if (snapshot != null && StringUtils.hasText(snapshot.systemPrompt())) {
            return snapshot.systemPrompt();
        }
        return CampusAssistantPrompts.SYSTEM_PROMPT;
    }

    /** 写入 ai_message.prompt_version 的版本标识，便于回溯回答是由哪一版提示词产生。 */
    public static String promptVersion() {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            return CampusAssistantPrompts.PROMPT_VERSION;
        }
        return snapshot.promptCode() + "@v" + snapshot.versionNo();
    }

    /** 当前是否使用数据库版本（管理端状态展示用）。 */
    public static boolean databaseBacked() {
        Snapshot snapshot = CURRENT.get();
        return snapshot != null && StringUtils.hasText(snapshot.systemPrompt());
    }

    public static void update(Snapshot snapshot) {
        CURRENT.set(snapshot);
    }

    public static void clear() {
        CURRENT.set(null);
    }
}
