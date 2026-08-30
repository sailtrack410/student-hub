package com.studenthub.modules.screen.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.screen.controller.dto.ScreenOverviewResponse;
import com.studenthub.modules.screen.service.ScreenRateLimiter;
import com.studenthub.modules.screen.service.ScreenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开大屏只读接口：免登录，白名单 + IP 限流，仅返回脱敏的运营统计数据。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/screen")
@Tag(name = "公开-数据大屏")
@RequiredArgsConstructor
public class PublicScreenController {

    private final ScreenService screenService;
    private final ScreenRateLimiter rateLimiter;

    @GetMapping("/overview")
    @Operation(summary = "大屏运营总览", description = "免登录只读；按 IP 限流；仅返回脱敏统计数据。",
            operationId = "getPublicScreenOverview")
    public Result<ScreenOverviewResponse> overview(HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(clientKey(request))) {
            throw new BusinessException(ErrorCode.PUBLIC_RATE_LIMITED);
        }
        return Result.success(screenService.overview());
    }

    /** 优先取反向代理透传的真实 IP，防止伪造 X-Forwarded-For 绕过限流时以首段为准 */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}