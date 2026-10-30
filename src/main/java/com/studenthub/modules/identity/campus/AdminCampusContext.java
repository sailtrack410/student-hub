package com.studenthub.modules.identity.campus;

import java.util.List;

/**
 * 管理端当前请求的校区上下文。
 *
 * <p>线程绑定只在一次 /api/admin/** 请求内有效，由
 * {@link AdminCampusContextInterceptor} 在请求结束时清理，避免线程池复用时串校区。</p>
 */
public final class AdminCampusContext {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private AdminCampusContext() {
    }

    public static void bind(Snapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static Snapshot current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * @param campusId           当前选中的校区；管理端所有业务写操作都必须落在具体校区
     * @param userId             当前管理员
     * @param platformAdmin      是否拥有 platform:all
     * @param accessibleCampusIds 允许切换的校区 ID
     */
    public record Snapshot(Long campusId, Long userId, boolean platformAdmin, List<Long> accessibleCampusIds) {
    }
}
