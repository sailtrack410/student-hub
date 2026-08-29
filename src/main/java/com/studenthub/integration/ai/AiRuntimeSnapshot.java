package com.studenthub.integration.ai;

/**
 * 当前生效的 AI 运行时配置快照。由管理后台保存时通过 {@link AiRuntimeConfig#update} 刷新。
 *
 * @param enabled                是否启用 AI
 * @param provider               供应商标识，当前仅支持 OPENAI_COMPATIBLE
 * @param baseUrl                兼容 OpenAI 协议的 base-url
 * @param apiKey                 接口密钥（已解析，未脱敏，仅内存使用）
 * @param model                  模型名
 * @param connectTimeoutSeconds  连接超时
 * @param readTimeoutSeconds     读取超时
 * @param maxInputCharacters     单次请求最大输入字符数
 * @param hourlyMessageLimit     单用户每小时消息上限
 * @param historyWindow          携带历史消息条数
 * @param maxMessageCharacters   单条用户消息最大字符数
 * @param version                配置版本（对应实体乐观锁版本）
 */
public record AiRuntimeSnapshot(
        boolean enabled,
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxInputCharacters,
        int hourlyMessageLimit,
        int historyWindow,
        int maxMessageCharacters,
        int version) {
}