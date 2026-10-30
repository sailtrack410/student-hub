package com.studenthub.modules.push.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 推送通道注册表：按渠道编码路由到具体 {@link PushProvider}，并统一兜底异常。
 *
 * <p>未知渠道或 provider 抛异常时，返回明确的 NOT_CONFIGURED / FAILED，
 * 绝不静默丢弃，也不伪造成功。</p>
 */
@Slf4j
@Component
public class PushProviderRegistry {

    /** 通道能力状态（供管理端展示「哪些通道真的能发」）。 */
    public record ChannelStatus(String channel, boolean configured, String reason) {
    }

    private final Map<String, PushProvider> providers = new LinkedHashMap<>();

    public PushProviderRegistry(List<PushProvider> providerList) {
        for (PushProvider provider : providerList) {
            providers.put(provider.channel().toUpperCase(Locale.ROOT), provider);
        }
    }

    public PushProvider resolve(String channel) {
        if (channel == null) {
            return null;
        }
        return providers.get(channel.trim().toUpperCase(Locale.ROOT));
    }

    public List<PushProvider> all() {
        return List.copyOf(providers.values());
    }

    public List<ChannelStatus> statuses() {
        List<ChannelStatus> result = new ArrayList<>();
        for (PushProvider provider : all()) {
            result.add(new ChannelStatus(provider.channel(), provider.configured(), provider.unconfiguredReason()));
        }
        return result;
    }

    /** 执行投递；任何异常都转成 FAILED 结果，避免单条失败拖垮整个任务。 */
    public PushDeliveryResult send(String channel, PushMessage message) {
        PushProvider provider = resolve(channel);
        if (provider == null) {
            return PushDeliveryResult.notConfigured("未知推送渠道：" + channel);
        }
        if (!provider.configured()) {
            String reason = provider.unconfiguredReason();
            return PushDeliveryResult.notConfigured(reason == null ? "通道未配置" : reason);
        }
        try {
            PushDeliveryResult result = provider.send(message);
            if (result == null) {
                log.warn("推送通道返回空结果, channel={}, taskId={}", channel, message.taskId());
                return PushDeliveryResult.failed("PUSH_EMPTY_RESULT", "通道返回空结果");
            }
            return result;
        } catch (RuntimeException exception) {
            log.warn("推送通道异常, channel={}, taskId={}, userId={}", channel, message.taskId(), message.userId(), exception);
            return PushDeliveryResult.failed("PUSH_PROVIDER_ERROR", "通道调用异常");
        }
    }
}
