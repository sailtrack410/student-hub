package com.studenthub.modules.identity.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.UpdateIdentityRequest;
import com.studenthub.modules.identity.controller.dto.UpdateProfileRequest;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.BearerTokenResolver;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.IdentityAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/profile")
@Tag(name = "小程序-个人中心")
@RequiredArgsConstructor
public class AppProfileController {

    private final IdentityAuthService identityAuthService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(summary = "更新个人资料", description = "修改当前用户昵称，返回刷新后的用户信息。",
            operationId = "updateProfile")
    public Result<CurrentUserResponse> updateProfile(@RequestBody @Valid UpdateProfileRequest request,
                                                     HttpServletRequest httpRequest) {
        String rawToken = requireRawToken(httpRequest);
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.updateProfile(rawToken, current, request.displayName()));
    }

    @PostMapping("/identity")
    @Operation(summary = "设置本人身份", description = "学生/教师可自行申报身份标识，用于后续按身份做功能隔离；"
            + "角色权限仍由平台分配，不受此处影响。返回刷新后的用户信息。", operationId = "updateIdentityType")
    public Result<CurrentUserResponse> updateIdentityType(@RequestBody @Valid UpdateIdentityRequest request,
                                                          HttpServletRequest httpRequest) {
        String rawToken = requireRawToken(httpRequest);
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.updateIdentityType(rawToken, current, request.identityType()));
    }

    private String requireRawToken(HttpServletRequest request) {
        return BearerTokenResolver.resolve(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
