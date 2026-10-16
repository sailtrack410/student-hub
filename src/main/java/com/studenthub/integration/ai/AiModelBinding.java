package com.studenthub.integration.ai;

/**
 * 一个已解析的模型绑定：默认模型 → 所属供应商的连接信息。
 *
 * @param providerId 供应商 ID
 * @param baseUrl    兼容 OpenAI 协议的 base-url
 * @param apiKey     接口密钥（未脱敏，仅内存使用）
 * @param model      模型名
 * @param protocol   调用协议：OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES
 * @param streaming  供应商是否支持流式输出（SSE）
 */
public record AiModelBinding(
        Long providerId,
        String baseUrl,
        String apiKey,
        String model,
        String protocol,
        boolean streaming) {

    public static final String PROTOCOL_OPENAI_CHAT = "OPENAI_CHAT";
    public static final String PROTOCOL_ANTHROPIC_MESSAGES = "ANTHROPIC_MESSAGES";
    public static final String PROTOCOL_OPENAI_RESPONSES = "OPENAI_RESPONSES";

    /** 兼容旧调用点：默认 OpenAI 兼容协议 + 支持流式 */
    public AiModelBinding(Long providerId, String baseUrl, String apiKey, String model) {
        this(providerId, baseUrl, apiKey, model, PROTOCOL_OPENAI_CHAT, true);
    }

    public String protocolOrDefault() {
        return protocol == null ? PROTOCOL_OPENAI_CHAT : protocol;
    }
}
