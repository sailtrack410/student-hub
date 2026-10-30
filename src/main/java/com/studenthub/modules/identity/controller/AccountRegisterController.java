package com.studenthub.modules.identity.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.EmailCodeResponse;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.controller.dto.RegisterRequest;
import com.studenthub.modules.identity.controller.dto.ResetPasswordRequest;
import com.studenthub.modules.identity.controller.dto.SendEmailCodeRequest;
import com.studenthub.modules.identity.service.IdentityAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/auth")
@Tag(name = "通用-账号注册", description = "校园邮箱验证码、注册与找回密码")
@RequiredArgsConstructor
public class AccountRegisterController {

    private final IdentityAuthService identityAuthService;

    @PostMapping("/email-code")
    @Operation(summary = "发送校园邮箱验证码", description = "优先真实 SMTP 发信；dev/test 未配置 SMTP 时在响应中返回 devCode，生产未配置返回 AUTH_503_03。",
            operationId = "sendCampusEmailCode")
    public Result<EmailCodeResponse> sendCode(@RequestBody @Valid SendEmailCodeRequest request) {
        return Result.success(identityAuthService.sendEmailCode(request));
    }

    @PostMapping("/register")
    @Operation(summary = "账号注册", description = "使用校园邮箱验证码注册，成功后直接返回登录会话。",
            operationId = "registerAccount")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        return Result.success(identityAuthService.register(request));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "找回密码", description = "账号 + 校园邮箱验证码重置密码。",
            operationId = "resetPassword")
    public Result<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        identityAuthService.resetPassword(request);
        return Result.success();
    }
}
