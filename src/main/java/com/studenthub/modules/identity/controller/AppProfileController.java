package com.studenthub.modules.identity.controller;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.controller.dto.AccountCredentialsResponse;
import com.studenthub.modules.identity.controller.dto.BindEmailRequest;
import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.QqBindRequest;
import com.studenthub.modules.identity.controller.dto.UpdateAccountCredentialsRequest;
import com.studenthub.modules.identity.controller.dto.UpdatePhoneRequest;
import com.studenthub.modules.identity.controller.dto.UpdatePhoneWechatRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/profile")
@Tag(name = "小程序-个人中心", description = "昵称头像与手机号维护")
@RequiredArgsConstructor
public class AppProfileController {

    private final IdentityAuthService identityAuthService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(summary = "更新个人资料", description = "修改昵称与头像（头像需先上传并传文件 ID），至少提供一项。",
            operationId = "updateProfile")
    public Result<CurrentUserResponse> updateProfile(@RequestBody @Valid UpdateProfileRequest request,
                                                     HttpServletRequest httpRequest) {
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.updateProfile(
                requireRawToken(httpRequest), current, request.displayName(), request.avatarFileId()));
    }

    @GetMapping("/credentials")
    @Operation(summary = "查询登录账号与密码状态", operationId = "getAccountCredentials")
    public Result<AccountCredentialsResponse> credentials() {
        return Result.success(identityAuthService.credentials(currentUserProvider.requireCurrentUser()));
    }

    @PostMapping("/credentials")
    @Operation(summary = "设置登录账号与密码", description = "设置后可使用该账号（用户名）与密码登录；已设置密码时需提供当前密码。",
            operationId = "updateAccountCredentials")
    public Result<AccountCredentialsResponse> updateCredentials(
            @RequestBody @Valid UpdateAccountCredentialsRequest request,
            HttpServletRequest httpRequest) {
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.updateCredentials(
                requireRawToken(httpRequest), current, request));
    }

    @PostMapping("/qq")
    @Operation(summary = "绑定 QQ", description = "已登录账号重新走 QQ 授权后绑定；未自定义的昵称/头像会用 QQ 资料覆盖。",
            operationId = "bindQqAccount")
    public Result<CurrentUserResponse> bindQq(@RequestBody @Valid QqBindRequest request,
                                              HttpServletRequest httpRequest) {
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.bindQq(
                requireRawToken(httpRequest), current, request.accessToken(), request.openId()));
    }

    @PostMapping("/email")
    @Operation(summary = "绑定校园邮箱", description = "绑定后可用于找回密码；需先获取 BIND 用途验证码。",
            operationId = "bindCampusEmail")
    public Result<AccountCredentialsResponse> bindEmail(@RequestBody @Valid BindEmailRequest request,
                                                        HttpServletRequest httpRequest) {
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.bindEmail(
                requireRawToken(httpRequest), current, request));
    }

    @PostMapping("/phone")
    @Operation(summary = "手动填写手机号", description = "微信手机号能力不可用时的备选方式。", operationId = "updatePhoneManual")
    public Result<CurrentUserResponse> updatePhone(@RequestBody @Valid UpdatePhoneRequest request,
                                                   HttpServletRequest httpRequest) {
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.updatePhone(
                requireRawToken(httpRequest), current, request.phone()));
    }

    @PostMapping("/phone/wechat")
    @Operation(summary = "绑定微信手机号", description = "使用 button open-type=getPhoneNumber 的 code 换取微信绑定手机号；"
            + "需要小程序完成微信认证，未认证环境请使用手动填写。", operationId = "updatePhoneByWechat")
    public Result<CurrentUserResponse> updatePhoneByWechat(
            @RequestBody @Valid UpdatePhoneWechatRequest request, HttpServletRequest httpRequest) {
        AuthenticatedUser current = currentUserProvider.requireCurrentUser();
        return Result.success(identityAuthService.updatePhoneByWechat(
                requireRawToken(httpRequest), current, request.code()));
    }

    private String requireRawToken(HttpServletRequest request) {
        return BearerTokenResolver.resolve(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
