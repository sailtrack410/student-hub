package com.studenthub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 直连 HttpClient.Builder 工厂（已内置 NO_PROXY）：AI 供应商请求显式绕过系统代理，
     * 避免开发机代理（如 Clash）把可直连的国内线路请求路由到境外导致超时。
     *
     * <p>只统一代理策略，超时由调用方按场景自行设置：
     * AI 供应商路径用运行时快照的动态超时（OpenAiCompatibleProvider / AiSpeechServiceImpl），
     * 管理端探活用固定短超时（AiProviderAdminServiceImpl）。
     * 全仓 NO_PROXY 只允许出现在本方法一处。
     */
    @Bean
    public HttpClient.Builder directHttpClientBuilder() {
        return HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY);
    }
}
