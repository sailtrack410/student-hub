package com.studenthub.modules.push.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 外部推送通道基类（FCM / APNs / Web Push）。
 *
 * <p>本期不引入任何厂商 SDK（依赖原则：不新增未锁定依赖），因此这些通道的真实投递能力为
 * <b>未实现</b>：{@link #configured()} 恒为 false，{@link #send(PushMessage)} 一律返回
 * NOT_CONFIGURED 并带明确原因。任务统计会把这类结果单列到 notConfiguredCount，
 * 不计入成功、也不计入失败，因此不会出现「假到达」。</p>
 */
@Slf4j
public abstract class UnconfiguredPushProvider implements PushProvider {

    private final String channel;
    private final PushProviderProperties.External config;

    protected UnconfiguredPushProvider(String channel, PushProviderProperties.External config) {
        this.channel = channel;
        this.config = config;
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public boolean configured() {
        // 传输层未实现，即使填了凭证也不能声称可投递。
        return false;
    }

    @Override
    public String unconfiguredReason() {
        if (config == null || !config.isEnabled()) {
            return channel + " 通道未启用（studenthub.push." + configKey() + ".enabled=false）";
        }
        if (!StringUtils.hasText(config.getCredentials())) {
            return channel + " 已启用但未配置凭证（studenthub.push." + configKey() + ".credentials 为空）";
        }
        return channel + " 凭证已配置，但本期未引入厂商 SDK，投递能力未实现（不会伪造到达）";
    }

    @Override
    public PushDeliveryResult send(PushMessage message) {
        String reason = unconfiguredReason();
        log.debug("推送通道未配置, channel={}, taskId={}, reason={}", channel, message.taskId(), reason);
        return PushDeliveryResult.notConfigured(reason);
    }

    /** 配置键片段：fcm / apns / webpush。 */
    protected abstract String configKey();
}
