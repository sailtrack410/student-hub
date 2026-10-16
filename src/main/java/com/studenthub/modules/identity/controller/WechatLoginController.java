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
@Tag(name = "通用-微信登录", description = "微信登录与开发固定账号登录")
@RequiredArgsConstructor
public class WechatLoginController {

    private final IdentityAuthService identityAuthService;

    @PostMapping("/wechat-login")
    @Operation(summary = "微信登录", description = "使用微信 wx.login() 获取的 code 登录；首次登录会自动创建学生基础账号。", operationId = "wechatLogin")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "仅提交微信 wx.login() 返回的 code")
    public Result<LoginResponse> login(@RequestBody @Valid WechatLoginRequest request) {
        return Result.success(identityAuthService.wechatLogin(request.code()));
    }
}
