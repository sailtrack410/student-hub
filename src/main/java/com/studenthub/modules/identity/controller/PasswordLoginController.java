package com.studenthub.modules.identity.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.controller.dto.PasswordLoginRequest;
import com.studenthub.modules.identity.service.IdentityAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/auth")
@Tag(name = "通用-密码登录", description = "开发环境密码登录（生产关闭）")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studenthub.security", name = "password-login-enabled", havingValue = "true")
public class PasswordLoginController {

    private final IdentityAuthService identityAuthService;

    @PostMapping("/login")
    @Operation(summary = "开发环境密码登录", description = "仅用于 dev/test 环境调试，生产环境关闭。", operationId = "passwordLogin")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "提交开发环境账号和密码")
    public Result<LoginResponse> login(@RequestBody @Valid PasswordLoginRequest request) {
        return Result.success(identityAuthService.login(request.username(), request.password()));
    }

    @PostMapping("/dev-wechat-login")
    @Operation(summary = "开发环境固定账号登录", description = "模拟微信登录的固定调试账号，首次登录自动建号，"
            + "之后复用同一用户，便于本地联调；生产环境关闭。", operationId = "devWechatLogin")
    public Result<LoginResponse> devWechatLogin() {
        return Result.success(identityAuthService.devWechatLogin());
    }
}
