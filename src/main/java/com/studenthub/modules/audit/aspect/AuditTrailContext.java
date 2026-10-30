package com.studenthub.modules.audit.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计「变更前」上下文：Service 在修改前把 before 快照放进来，
 * {@link AdminWriteAuditAspect} 在写操作成功后读取并落库。
 *
 * <p>用 request attribute 承载，随请求结束自动释放，不会在线程池里泄漏；
 * 异步线程/定时任务没有请求上下文时直接忽略。</p>
 */
public final class AuditTrailContext {

    private static final String BEFORE_ATTR = AuditTrailContext.class.getName() + ".BEFORE";

    private AuditTrailContext() {
    }

    /** 记录变更前快照（可为任意可序列化对象，如 Map/record/Entity DTO）。 */
    public static void setBefore(Object before) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.setAttribute(BEFORE_ATTR, before);
        }
    }

    /** 取变更前快照；没有请求上下文或未设置时返回 null。 */
    public static Object getBefore() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getAttribute(BEFORE_ATTR);
    }

    public static void clear() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.removeAttribute(BEFORE_ATTR);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
