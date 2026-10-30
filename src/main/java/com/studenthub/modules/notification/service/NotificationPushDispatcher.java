package com.studenthub.modules.notification.service;

import com.studenthub.modules.community.realtime.CommunityWebSocketHandler;
import com.studenthub.modules.notification.entity.NotificationEntity;
import com.studenthub.modules.push.entity.PushTokenEntity;
import com.studenthub.modules.push.mapper.PushTokenMapper;
import com.studenthub.modules.push.provider.PushDeliveryResult;
import com.studenthub.modules.push.provider.PushDeliveryStatus;
import com.studenthub.modules.push.provider.PushMessage;
import com.studenthub.modules.push.provider.PushProvider;
import com.studenthub.modules.push.provider.PushProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 站内通知实时推送 + 设备（OS）推送分发入口。
 *
 * <p>在线用户通过 /ws/community 的 NOTIFICATION 事件实时送达；
 * 已登记设备 Token 的用户再按平台走 OS 推送通道（FCM/APNs/Web Push）。
 * 这些通道当前未接入厂商 SDK，{@link PushProviderRegistry} 会返回 NOT_CONFIGURED，
 * 这里只记录真实状态，不伪造到达、不写假的投递成功。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushDispatcher {

    private final CommunityWebSocketHandler webSocketHandler;
    private final PushTokenMapper pushTokenMapper;
    private final PushProviderRegistry pushProviderRegistry;

    public void dispatch(NotificationEntity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventKey", "notification:" + entity.getId());
        data.put("notificationId", entity.getId());
        data.put("notificationType", entity.getNotificationType());
        data.put("title", entity.getTitle());
        data.put("content", entity.getContent());
        data.put("relatedType", entity.getRelatedType());
        data.put("relatedId", entity.getRelatedId());
        data.put("createdAt", entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        webSocketHandler.publishEvent(entity.getRecipientId(), "NOTIFICATION", data);

        List<PushTokenEntity> tokens = pushTokenMapper.findByUser(entity.getRecipientId());
        if (tokens.isEmpty()) {
            return;
        }
        // 按平台聚合到通道，避免同平台多设备重复调用；通道未配置时明确记录原因。
        Map<String, List<PushTokenEntity>> byChannel = new LinkedHashMap<>();
        for (PushTokenEntity token : tokens) {
            String channel = channelOf(token.getPlatform());
            if (channel != null) {
                byChannel.computeIfAbsent(channel, key -> new ArrayList<>()).add(token);
            }
        }
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, List<PushTokenEntity>> entry : byChannel.entrySet()) {
            String channel = entry.getKey();
            PushProvider provider = pushProviderRegistry.resolve(channel);
            if (provider == null || !provider.configured()) {
                log.info("OS 推送通道不可用，未发送, recipientId={}, channel={}, reason={}",
                        entity.getRecipientId(), channel,
                        provider == null ? "未知通道" : provider.unconfiguredReason());
                failures.add(channel + ":" + (provider == null ? "未知通道" : provider.unconfiguredReason()));
                continue;
            }
            PushDeliveryResult result = pushProviderRegistry.send(channel, new PushMessage(
                    null, entity.getRecipientId(), entity.getTitle(), entity.getContent(), data));
            if (!PushDeliveryStatus.isSuccess(result.status())) {
                failures.add(channel + ":" + result.status() + ":" + result.message());
                log.warn("OS 推送未成功, recipientId={}, channel={}, status={}, message={}",
                        entity.getRecipientId(), channel, result.status(), result.message());
            }
        }
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("; ", failures));
    }

    /** 平台 → 推送通道：ANDROID→FCM、IOS→APNS、WEB/BROWSER→WEBPUSH；未知平台返回 null。 */
    private String channelOf(String platform) {
        if (platform == null) {
            return null;
        }
        return switch (platform.trim().toUpperCase(Locale.ROOT)) {
            case "ANDROID", "FCM" -> "FCM";
            case "IOS", "APNS" -> "APNS";
            case "WEB", "BROWSER", "WEBPUSH" -> "WEBPUSH";
            default -> null;
        };
    }
}
