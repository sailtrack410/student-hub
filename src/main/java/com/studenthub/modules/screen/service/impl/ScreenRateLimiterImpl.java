package com.studenthub.modules.screen.service.impl;

import com.studenthub.modules.screen.service.ScreenRateLimiter;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 固定窗口限流：默认每 IP 每分钟 60 次（大屏轮询约 4 次/分钟，足够冗余）。
 * 周期性清理过期窗口，避免 Map 无限增长。
 */
@Component
public class ScreenRateLimiterImpl implements ScreenRateLimiter {

    /** 窗口长度 60 秒 */
    private static final long WINDOW_MILLIS = 60_000L;
    /** 窗口内最大请求数 */
    private static final int LIMIT = 60;
    /** 超过该数量的 key 才触发清理扫描 */
    private static final int CLEAN_THRESHOLD = 1024;

    /** key -> long[]{windowStartMillis, count} */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String clientKey) {
        return tryAcquire(clientKey, LIMIT);
    }

    @Override
    public boolean tryAcquire(String clientKey, int limit) {
        if (clientKey == null || clientKey.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        boolean allowed = true;
        long[] window = windows.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing[0] >= WINDOW_MILLIS) {
                return new long[]{now, 1};
            }
            existing[1]++;
            return existing;
        });
        if (window[1] > limit) {
            allowed = false;
        }
        if (windows.size() > CLEAN_THRESHOLD) {
            cleanExpired(now);
        }
        return allowed;
    }

    private void cleanExpired(long now) {
        Iterator<Map.Entry<String, long[]>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, long[]> entry = iterator.next();
            if (now - entry.getValue()[0] >= WINDOW_MILLIS) {
                iterator.remove();
            }
        }
    }
}