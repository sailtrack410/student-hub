package com.studenthub.modules.device.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.device.controller.dto.DeviceResponse;
import com.studenthub.modules.device.service.DeviceSessionService;
import com.studenthub.modules.identity.security.BearerTokenResolver;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 设备与安全管理（API需求清单 3.x）：当前设备判定与踢线都基于会话 token 摘要，
 * 登出请走 /api/common/auth/logout（当前设备不允许在本模块下线）。
 */
@RestController
@RequestMapping("/api/app/devices")
@Tag(name = "小程序-我的设备", description = "在线设备列表、踢除设备与一键退出其他设备")
@RequiredArgsConstructor
public class AppDeviceController {

    private final DeviceSessionService deviceSessionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询我的在线设备列表", description = "登录设备登记（UA 解析），isCurrent 标识当前请求设备。",
            operationId = "listMyDevices")
    public Result<List<DeviceResponse>> list(HttpServletRequest request) {
        String rawToken = BearerTokenResolver.resolve(request).orElse(null);
        return Result.success(deviceSessionService.listDevices(
                currentUserProvider.requireCurrentUser(), rawToken, request.getHeader("User-Agent")));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "踢除/下线指定设备", description = "该设备会话立即失效；当前设备不允许在此下线（请走退出登录）。",
            operationId = "kickDevice")
    public Result<Boolean> kick(@PathVariable String deviceId, HttpServletRequest request) {
        deviceSessionService.kickDevice(currentUserProvider.requireCurrentUser(), deviceId,
                BearerTokenResolver.resolve(request).orElse(null));
        return Result.success(true);
    }

    @PostMapping("/logout-others")
    @Operation(summary = "一键退出其他所有设备", description = "除当前设备外全部会话失效，返回踢除数。",
            operationId = "logoutOtherDevices")
    public Result<Map<String, Integer>> logoutOthers(HttpServletRequest request) {
        int revoked = deviceSessionService.logoutOthers(currentUserProvider.requireCurrentUser(),
                BearerTokenResolver.resolve(request).orElse(null));
        return Result.success(Map.of("revokedCount", revoked));
    }
}
