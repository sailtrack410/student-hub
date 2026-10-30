package com.studenthub.modules.push.provider;

import com.studenthub.modules.community.realtime.CommunityWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站内 WebSocket 通道：用户在线时写入会话，在线写入成功记为 DELIVERED；离线记为 SENT。
 *
 * <p>复用既有 /ws/community 长连接（NOTIFICATION 事件），不新增连接协议。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPushProvider implements PushProvider {

    public static final String CHANNEL = "WS";

    private final CommunityWebSocketHandler webSocketHandler;

    @Override
    public String channel() {
        return CHANNEL;
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
        Map<String, Object> payload = new LinkedHashMap<>();
        if (message.data() != null) {
            payload.putAll(message.data());
        }
        payload.put("taskId", message.taskId());
        payload.put("title", message.title());
        payload.put("content", message.content());
        boolean online = webSocketHandler.isOnline(message.userId());
        if (!online) {
            // 离线不代表失败：用户下次上线可从站内通知列表读取，但不计为「到达」。
            return PushDeliveryResult.sent("用户离线，已标记为待拉取（不计到达）");
        }
        try {
            webSocketHandler.publishEvent(message.userId(), "PUSH", payload);
            return PushDeliveryResult.delivered("WebSocket 在线写入成功");
        } catch (RuntimeException exception) {
            log.warn("WebSocket 推送写入失败, userId={}, taskId={}", message.userId(), message.taskId(), exception);
            return PushDeliveryResult.failed("PUSH_WS_ERROR", "WebSocket 写入失败");
        }
    }
}
