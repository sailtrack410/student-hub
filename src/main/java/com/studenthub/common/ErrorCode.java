package com.studenthub.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_PARAMETER("COMMON_400", "请求参数不合法"),
    UNAUTHORIZED("AUTH_401", "请先完成身份认证"),
    FORBIDDEN("AUTH_403", "没有执行该操作的权限"),
    NOT_FOUND("COMMON_404", "资源不存在"),
    AI_NOT_CONFIGURED("AI_503", "AI 服务尚未配置"),
    AI_PROVIDER_ERROR("AI_500", "AI 服务暂时不可用"),
    INTERNAL_ERROR("COMMON_500", "系统繁忙，请稍后重试");

    private final String code;
    private final String message;
}
