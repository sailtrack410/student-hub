package com.studenthub.modules.identity.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.controller.dto.QqLoginRequest;
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
@Tag(name = "通用-QQ登录", description = "QQ 授权登录")
@RequiredArgsConstructor
public class QqLoginController {

    private final IdentityAuthService identityAuthService;

    @PostMapping("/qq-login")
    @Operation(summary = "QQ 登录", description = "使用 QQ SDK 授权得到的 accessToken 登录；服务端反查 openId 建立身份，"
            + "首次登录自动创建学生基础账号。", operationId = "qqLogin")
    public Result<LoginResponse> login(@RequestBody @Valid QqLoginRequest request) {
        return Result.success(identityAuthService.qqLogin(request.accessToken(), request.openId()));
    }
}
