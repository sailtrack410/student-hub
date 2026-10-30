package com.studenthub.modules.push.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.push.controller.dto.PushTokenResponse;
import com.studenthub.modules.push.controller.dto.RegisterPushTokenRequest;
import com.studenthub.modules.push.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/push")
@Tag(name = "小程序-系统推送", description = "推送 Token 登记与解绑")
@RequiredArgsConstructor
public class AppPushTokenController {
    private final PushTokenService pushTokenService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/tokens")
    @Operation(summary = "注册推送 Token", operationId = "registerPushToken")
    public Result<PushTokenResponse> register(@RequestBody @Valid RegisterPushTokenRequest request) {
        return Result.success(pushTokenService.register(
                currentUserProvider.requireCurrentUser().userId(), request));
    }

    @DeleteMapping("/tokens")
    @Operation(summary = "解绑推送 Token", operationId = "unregisterPushToken")
    public Result<Void> unregister(@RequestParam String token) {
        pushTokenService.unregister(currentUserProvider.requireCurrentUser().userId(), token);
        return Result.success();
    }
}
