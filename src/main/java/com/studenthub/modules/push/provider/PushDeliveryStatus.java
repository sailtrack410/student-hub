package com.studenthub.modules.push.provider;

/** 推送投递状态常量：与 push_task_delivery.status 及统计口径一一对应。 */
public final class PushDeliveryStatus {

    /** 已交给通道，但无到达证明（如离线 WebSocket、日志通道）。 */
    public static final String SENT = "SENT";
    /** 通道确认到达（在线 WebSocket 写入成功）。 */
    public static final String DELIVERED = "DELIVERED";
    /** 用户点击（App 回调记录）。 */
    public static final String CLICKED = "CLICKED";
    /** 通道明确失败。 */
    public static final String FAILED = "FAILED";
    /** 通道未配置：既不算成功也不算失败，统计单列。 */
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    /** 频控命中 / 无效接收人，主动跳过。 */
    public static final String SKIPPED = "SKIPPED";
    /** 初始态。 */
    public static final String PENDING = "PENDING";

    private PushDeliveryStatus() {
    }

    /** 是否视为「成功送达通道」。 */
    public static boolean isSuccess(String status) {
        return SENT.equals(status) || DELIVERED.equals(status) || CLICKED.equals(status);
    }

    /** 是否视为「已到达客户端」。 */
    public static boolean isDelivered(String status) {
        return DELIVERED.equals(status) || CLICKED.equals(status);
    }
}
