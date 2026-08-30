package com.studenthub.modules.screen.service;

/**
 * 公开大屏接口的简易 IP 限流：固定窗口计数，拦截异常高频访问。
 */
public interface ScreenRateLimiter {

    /**
     * 尝试为指定 IP 记一次访问。
     *
     * @return true=放行；false=超出窗口内限额
     */
    boolean tryAcquire(String clientKey);

    /**
     * 指定窗口内限额的限流（key 建议带能力前缀，如 "chat:" + ip，与普通访问隔离计数）。
     */
    boolean tryAcquire(String clientKey, int limit);
}