package com.studenthub.modules.identity.campus;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 把所有 /api/admin/** 请求绑定到具体校区；请求结束清理 ThreadLocal。
 */
@Component
@RequiredArgsConstructor
public class AdminCampusContextInterceptor implements HandlerInterceptor {

    private final AdminCampusContextService campusContextService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE 完成后会再次以 ASYNC 分派回 DispatcherServlet；此时异步线程没有 SecurityContext，
        // 不能重新激活校区上下文，否则已完成的事件流会被错误页覆盖并导致 chunked 响应不完整。
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String raw = request.getHeader(AdminCampusContextService.CAMPUS_HEADER);
        Long requested = parseCampusId(raw);
        campusContextService.activate(requested);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AdminCampusContext.clear();
    }

    private Long parseCampusId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
