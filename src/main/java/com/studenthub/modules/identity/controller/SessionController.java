package com.studenthub.modules.identity.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.security.BearerTokenResolver;
import com.studenthub.modules.identity.service.IdentityAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/auth")
@Tag(name = "会话认证")
@RequiredArgsConstructor
public class SessionController {

    private final IdentityAuthService identityAuthService;

    @GetMapping("/me")
    @Operation(summary = "查询当前用户", operationId = "getCurrentUser")
    public Result<CurrentUserResponse> currentUser() {
        return Result.success(identityAuthService.currentUser());
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", operationId = "logout")
    public Result<Void> logout(HttpServletRequest request) {
        String rawToken = BearerTokenResolver.resolve(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        identityAuthService.logout(rawToken);
        return Result.success();
    }
}
