package com.studenthub.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_PARAMETER("COMMON_400", "请求参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("AUTH_401", "请先完成身份认证", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("AUTH_401_01", "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("AUTH_403_01", "账号已停用", HttpStatus.FORBIDDEN),
    FORBIDDEN("AUTH_403", "没有执行该操作的权限", HttpStatus.FORBIDDEN),
    NOT_FOUND("COMMON_404", "资源不存在", HttpStatus.NOT_FOUND),
    AI_NOT_CONFIGURED("AI_503", "AI 服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE),
    AI_PROVIDER_ERROR("AI_502", "AI 服务暂时不可用", HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR("COMMON_500", "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
