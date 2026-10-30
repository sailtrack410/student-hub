package com.studenthub.modules.community.security;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.systemconfig.service.SystemConfigChangedEvent;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 广告事件防刷护栏。它不是替代风控系统，而是先挡住最基础的高频刷量与无曝光硬点击：
 *
 * <ul>
 *   <li>用户级滑动窗口：曝光/落地事件默认 120 次/分钟，点击默认 30 次/分钟；</li>
 *   <li>点击归因：客户端回传 {@code clickId} 时，必须存在同一用户、同一广告、30 分钟内的曝光，
 *       且会话 ID 不能与曝光会话冲突；不传 clickId 的兼容请求仍可记录点击，但不会增加曝光归因；</li>
 *   <li>配置项保存在 {@code sys_config}，管理端保存后通过 {@link SystemConfigChangedEvent} 清缓存，下一批事件生效。</li>
 * </ul>
 *
 * <p>计数器是单实例内存实现，符合当前单体部署；多实例部署时应改为 Redis 滑动窗口，否则限流按实例数放大。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityAdEventGuard {

    public static final String KEY_EVENT_RATE = "community.ad_event_rate_per_minute";
    public static final String KEY_CLICK_RATE = "community.ad_click_rate_per_minute";
    public static final String KEY_ATTRIBUTION_REQUIRED = "community.ad_click_requires_impression";

    private static final int WINDOW_MILLIS = 60_000;
    private static final int DEFAULT_EVENT_RATE = 120;
    private static final int DEFAULT_CLICK_RATE = 30;
    private static final int CACHE_MILLIS = 30_000;
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final SystemConfigService systemConfigService;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private volatile Limits cachedLimits;
    private volatile long cachedAt;

    public record Limits(int eventRatePerMinute, int clickRatePerMinute, boolean clickAttributionRequired) {
    }

    public void checkImpression(AuthenticatedUser user) {
        check(user.userId(), "impression", limits().eventRatePerMinute());
    }

    public void checkClick(AuthenticatedUser user) {
        check(user.userId(), "click", limits().clickRatePerMinute());
    }

    public void checkLanding(AuthenticatedUser user) {
        check(user.userId(), "landing", limits().eventRatePerMinute());
    }

    public boolean clickAttributionRequired() {
        return limits().clickAttributionRequired();
    }

    @EventListener(SystemConfigChangedEvent.class)
    public void onConfigChanged(SystemConfigChangedEvent event) {
        cachedLimits = null;
    }

    private void check(Long userId, String kind, int limit) {
        if (userId == null || limit <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = userId + ":" + kind;
        Deque<Long> window = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                log.warn("广告事件触发限流, userId={}, kind={}, limit={}", userId, kind, limit);
                throw new BusinessException(ErrorCode.AD_EVENT_RATE_LIMITED);
            }
            window.addLast(now);
        }
        if (checks.incrementAndGet() % 512 == 0) {
            cleanup(now);
        }
    }

    private void cleanup(long now) {
        if (windows.size() <= MAX_TRACKED_KEYS / 2) {
            return;
        }
        windows.entrySet().removeIf(entry -> {
            Deque<Long> window = entry.getValue();
            synchronized (window) {
                while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                    window.pollFirst();
                }
                return window.isEmpty();
            }
        });
    }

    private Limits limits() {
        long now = System.currentTimeMillis();
        Limits current = cachedLimits;
        if (current != null && now - cachedAt < CACHE_MILLIS) {
            return current;
        }
        synchronized (this) {
            if (cachedLimits != null && now - cachedAt < CACHE_MILLIS) {
                return cachedLimits;
            }
            cachedLimits = new Limits(
                    configInt(KEY_EVENT_RATE, DEFAULT_EVENT_RATE, 0, 1000),
                    configInt(KEY_CLICK_RATE, DEFAULT_CLICK_RATE, 0, 300),
                    configBoolean(KEY_ATTRIBUTION_REQUIRED, true));
            cachedAt = now;
            return cachedLimits;
        }
    }

    private int configInt(String key, int fallback, int min, int max) {
        String raw = systemConfigService.getValue(key, null);
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean configBoolean(String key, boolean fallback) {
        String raw = systemConfigService.getValue(key, null);
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }
}
