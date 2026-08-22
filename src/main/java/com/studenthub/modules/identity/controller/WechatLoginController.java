package com.studenthub.modules.identity.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.controller.dto.WechatLoginRequest;
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
@Tag(name = "微信认证")
@RequiredArgsConstructor
public class WechatLoginController {

    private final IdentityAuthService identityAuthService;

    @PostMapping("/wechat-login")
    @Operation(summary = "微信登录", operationId = "wechatLogin")
    public Result<LoginResponse> login(@RequestBody @Valid WechatLoginRequest request) {
        return Result.success(identityAuthService.wechatLogin(request.code()));
    }
}
