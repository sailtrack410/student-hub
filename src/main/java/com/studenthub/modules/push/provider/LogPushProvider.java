package com.studenthub.modules.push.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志通道：始终可用，用于本地开发与管理端联调。
 *
 * <p>只把「已写入服务端日志」视为 SENT（无到达证明），不会返回 DELIVERED。</p>
 */
@Slf4j
@Component
public class LogPushProvider implements PushProvider {

    @Override
    public String channel() {
        return "LOG";
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public String unconfiguredReason() {
        return null;
    }

    @Override
    public PushDeliveryResult send(PushMessage message) {
        // 仅记录标题与任务号，避免日志泄露完整推送正文（正文可能含个人信息）。
        log.info("推送(LOG通道), taskId={}, userId={}, title={}",
                message.taskId(), message.userId(), message.title());
        return PushDeliveryResult.sent("已写入服务端日志");
    }
}
