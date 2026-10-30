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
    CAPTCHA_INVALID("AUTH_401_04", "验证码错误或已过期，请刷新后重试", HttpStatus.UNAUTHORIZED),
    WECHAT_LOGIN_FAILED("AUTH_401_02", "微信登录失败，请稍后重试", HttpStatus.UNAUTHORIZED),
    WECHAT_NOT_CONFIGURED("AUTH_503_01", "微信登录服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE),
    QQ_LOGIN_FAILED("AUTH_401_03", "QQ 登录失败，请稍后重试", HttpStatus.UNAUTHORIZED),
    QQ_NOT_CONFIGURED("AUTH_503_02", "QQ 登录服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_NOT_CONFIGURED("AUTH_503_03", "邮箱服务尚未配置，请联系管理员", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_SEND_FAILED("AUTH_502_01", "验证码发送失败，请稍后重试", HttpStatus.BAD_GATEWAY),
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
    AI_REQUEST_IN_PROGRESS("AI_409_01", "本次提问正在处理中，请稍后查看原会话", HttpStatus.CONFLICT),
    AI_REQUEST_CONFLICT("AI_409_02", "请求标识已用于其他内容，请发起新问题", HttpStatus.CONFLICT),
    AI_REQUEST_FAILED("AI_409_03", "本次提问已失败，请发起新问题", HttpStatus.CONFLICT),
    AI_REQUEST_CANCELLED("AI_409_04", "本次提问已取消，请发起新问题", HttpStatus.CONFLICT),
    AI_BUSY("AI_503_01", "AI 服务繁忙，请稍后重试本次提问", HttpStatus.SERVICE_UNAVAILABLE),
    AI_RATE_LIMITED("AI_429_01", "提问太频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    JW_AUTH_FAILED("JW_401_01", "账号、密码或验证码错误", HttpStatus.UNAUTHORIZED),
    JW_SESSION_EXPIRED("JW_401_02", "教务认证已失效，请重新认证", HttpStatus.UNAUTHORIZED),
    JW_ACCOUNT_ALREADY_BOUND("JW_409_01", "该账号已被绑定到其他用户", HttpStatus.CONFLICT),
    JW_RATE_LIMITED("JW_429_01", "操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    PHONE_EXCHANGE_FAILED("PHONE_400_01", "手机号获取失败，请手动填写", HttpStatus.BAD_REQUEST),
    PUBLIC_RATE_LIMITED("PUBLIC_429", "访问过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    COMMUNITY_REPORT_DUPLICATE("COMMUNITY_409_01", "你已举报过该内容，正在处理中", HttpStatus.CONFLICT),
    COMMUNITY_REPORT_HANDLED("COMMUNITY_409_02", "该举报已处理", HttpStatus.CONFLICT),
    COMMUNITY_REPORT_TARGET_INVALID("COMMUNITY_400_01", "举报对象不存在或不可举报", HttpStatus.BAD_REQUEST),
    COMMUNITY_REPORT_NOT_FOUND("COMMUNITY_404_01", "举报记录不存在", HttpStatus.NOT_FOUND),
    COMMUNITY_MUTED("COMMUNITY_403_01", "你已被限制发言，暂时无法发布内容", HttpStatus.FORBIDDEN),
    AD_EVENT_RATE_LIMITED("AD_429_01", "广告事件上报过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    AD_ATTRIBUTION_INVALID("AD_400_01", "点击归因无效：缺少同广告的有效曝光或已过期", HttpStatus.BAD_REQUEST),
    COMMUNITY_REPORT_RATE_LIMITED("COMMUNITY_429_01", "举报过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("COMMON_500", "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
