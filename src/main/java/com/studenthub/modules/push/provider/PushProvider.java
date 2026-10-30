package com.studenthub.modules.push.provider;

/**
 * 推送通道适配器：屏蔽 WebSocket / 日志 / FCM / APNs / Web Push 的差异。
 *
 * <p>约定：
 * <ul>
 *   <li>{@link #configured()} 必须如实反映「本进程能否真正投递」；未配置时不得返回成功；</li>
 *   <li>{@link #send(PushMessage)} 只能返回真实通道结果，禁止伪造 DELIVERED；</li>
 *   <li>实现类不得抛出异常给调用方（由 {@link PushProviderRegistry} 统一兜底），
 *       异常会被记录为 FAILED，而不是静默丢弃。</li>
 * </ul>
 */
public interface PushProvider {

    /** 渠道编码：LOG / WS / FCM / APNS / WEBPUSH。 */
    String channel();

    /** 当前进程是否具备真实投递能力。 */
    boolean configured();

    /** 未配置/降级原因（configured=true 时返回 null）。 */
    String unconfiguredReason();

    /** 执行一次投递。 */
    PushDeliveryResult send(PushMessage message);
}
