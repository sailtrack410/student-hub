package com.studenthub.modules.jwauth.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.security.BearerTokenResolver;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.jwauth.controller.dto.JwAuthRequest;
import com.studenthub.modules.jwauth.controller.dto.JwAuthResult;
import com.studenthub.modules.jwauth.controller.dto.JwCaptchaResponse;
import com.studenthub.modules.jwauth.controller.dto.JwProfileResponse;
import com.studenthub.modules.jwauth.controller.dto.JwStatusResponse;
import com.studenthub.modules.jwauth.service.JwAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/jw")
@Tag(name = "小程序-教务认证")
@RequiredArgsConstructor
public class AppJwAuthController {

    private final JwAuthService jwAuthService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/captcha")
    @Operation(summary = "获取教务验证码", description = "算术题验证码图片（base64），认证时需原样传回 uid。",
            operationId = "getJwCaptcha")
    public Result<JwCaptchaResponse> captcha() {
        return Result.success(jwAuthService.captcha());
    }

    @PostMapping("/auth")
    @Operation(summary = "教务系统认证", description = "使用统一身份账号、密码与算术验证码完成教务认证；"
            + "成功后按教务身份自动切换平台身份（学生/教师），同一教务账号仅可绑定一个平台用户。",
            operationId = "authJwAccount")
    public Result<JwAuthResult> auth(@RequestBody @Valid JwAuthRequest request, HttpServletRequest httpRequest) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        String rawToken = BearerTokenResolver.resolve(httpRequest)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return Result.success(jwAuthService.auth(userId, rawToken, request));
    }

    @GetMapping("/status")
    @Operation(summary = "查询教务认证状态", description = "返回未认证/已认证/已失效三态及绑定信息。",
            operationId = "getJwAuthStatus")
    public Result<JwStatusResponse> status() {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(jwAuthService.status(userId));
    }

    @PostMapping("/unbind")
    @Operation(summary = "解除教务绑定", description = "解除绑定并立即销毁托管的教务会话。", operationId = "unbindJwAccount")
    public Result<Void> unbind() {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        jwAuthService.unbind(userId);
        return Result.success();
    }

    @GetMapping("/profile")
    @Operation(summary = "查询教务个人信息", description = "从教务系统实时拉取个人信息；会话失效时返回已失效错误。",
            operationId = "getJwProfile")
    public Result<JwProfileResponse> profile() {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(jwAuthService.profile(userId));
    }
}
