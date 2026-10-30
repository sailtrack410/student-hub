package com.studenthub.modules.push.provider;

/**
 * 单个渠道的投递结果。
 *
 * @param status  见 {@link PushDeliveryStatus}
 * @param message 通道回执摘要（禁止写入凭证、Token 原文）
 * @param errorCode 失败/未配置时的错误码，成功为 null
 */
public record PushDeliveryResult(String status, String message, String errorCode) {

    public static PushDeliveryResult sent(String message) {
        return new PushDeliveryResult(PushDeliveryStatus.SENT, message, null);
    }

    public static PushDeliveryResult delivered(String message) {
        return new PushDeliveryResult(PushDeliveryStatus.DELIVERED, message, null);
    }

    public static PushDeliveryResult failed(String errorCode, String message) {
        return new PushDeliveryResult(PushDeliveryStatus.FAILED, message, errorCode);
    }

    public static PushDeliveryResult notConfigured(String message) {
        return new PushDeliveryResult(PushDeliveryStatus.NOT_CONFIGURED, message, "PUSH_NOT_CONFIGURED");
    }
}
