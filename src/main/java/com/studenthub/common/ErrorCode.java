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
    WECHAT_LOGIN_FAILED("AUTH_401_02", "微信登录失败，请稍后重试", HttpStatus.UNAUTHORIZED),
    WECHAT_NOT_CONFIGURED("AUTH_503_01", "微信登录服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE),
    ACCOUNT_DISABLED("AUTH_403_01", "账号已停用", HttpStatus.FORBIDDEN),
    FORBIDDEN("AUTH_403", "没有执行该操作的权限", HttpStatus.FORBIDDEN),
    NOT_FOUND("COMMON_404", "资源不存在", HttpStatus.NOT_FOUND),
    AI_NOT_CONFIGURED("AI_503", "AI 服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE),
    AI_PROVIDER_ERROR("AI_502", "AI 服务暂时不可用", HttpStatus.BAD_GATEWAY),
    ROLE_NOT_CONFIGURED("AUTH_500_01", "默认角色未配置，请联系管理员", HttpStatus.INTERNAL_SERVER_ERROR),
    CATALOG_ITEM_NOT_FOUND("CATALOG_404_01", "服务项不存在或已停用", HttpStatus.NOT_FOUND),
    CATALOG_CATEGORY_NOT_FOUND("CATALOG_404_02", "服务分类不存在", HttpStatus.NOT_FOUND),
    REQUEST_NOT_FOUND("REQUEST_404_01", "申请不存在", HttpStatus.NOT_FOUND),
    REQUEST_STATE_INVALID("REQUEST_409_01", "当前状态不允许该操作", HttpStatus.CONFLICT),
    REQUEST_DUPLICATE("REQUEST_409_02", "请勿重复提交", HttpStatus.CONFLICT),
    ORDER_NOT_FOUND("ORDER_404_01", "工单不存在", HttpStatus.NOT_FOUND),
    ORDER_STATE_INVALID("ORDER_409_01", "工单当前状态不允许该操作", HttpStatus.CONFLICT),
    ORDER_NOT_ASSIGNED_TO_CURRENT_USER("ORDER_403_01", "该工单未指派给你", HttpStatus.FORBIDDEN),
    EVALUATION_DUPLICATE("EVALUATION_409_01", "该申请已评价过", HttpStatus.CONFLICT),
    NOTIFICATION_NOT_FOUND("NOTIFICATION_404_01", "通知不存在", HttpStatus.NOT_FOUND),
    CONVERSATION_NOT_FOUND("AI_404_01", "会话不存在", HttpStatus.NOT_FOUND),
    AI_RATE_LIMITED("AI_429_01", "提问太频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("COMMON_500", "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
