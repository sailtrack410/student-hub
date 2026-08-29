package com.studenthub.integration.ai;

/**
 * 一个已解析的模型绑定：默认模型 → 所属供应商的连接信息。
 *
 * @param providerId 供应商 ID
 * @param baseUrl    兼容 OpenAI 协议的 base-url
 * @param apiKey     接口密钥（未脱敏，仅内存使用）
 * @param model      模型名
 */
public record AiModelBinding(
        Long providerId,
        String baseUrl,
        String apiKey,
        String model) {
}
