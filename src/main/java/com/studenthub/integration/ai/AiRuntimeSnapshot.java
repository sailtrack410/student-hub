package com.studenthub.integration.ai;

/**
 * 当前生效的 AI 运行时配置快照。由管理后台保存时通过 {@link AiRuntimeConfig#update} 刷新。
 *
 * @param enabled                全局总开关（ai_config.enabled）
 * @param chat                   默认聊天模型绑定（供应商启用且密钥已配置时才有值，可为 null）
 * @param vision                 默认图片识别模型绑定（可为 null，预留识图路由）
 * @param asr                    默认语音识别模型绑定（可为 null）
 * @param connectTimeoutSeconds  连接超时
 * @param readTimeoutSeconds     读取超时
 * @param maxInputCharacters     单次请求最大输入字符数
 * @param hourlyMessageLimit     单用户每小时消息上限
 * @param historyWindow          携带历史消息条数
 * @param maxMessageCharacters   单条用户消息最大字符数
 * @param version                配置版本（每次重建快照递增）
 */
public record AiRuntimeSnapshot(
        boolean enabled,
        AiModelBinding chat,
        AiModelBinding vision,
        AiModelBinding asr,
        AiModelBinding embedding,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxInputCharacters,
        int hourlyMessageLimit,
        int historyWindow,
        int maxMessageCharacters,
        int version) {
}
