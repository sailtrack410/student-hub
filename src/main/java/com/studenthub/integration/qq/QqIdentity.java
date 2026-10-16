package com.studenthub.integration.qq;

/**
 * QQ 互联换取的用户身份。unionId 来自 /oauth2.0/me?unionid=1 反查，可能为空：
 * 仅当 QQ 互联应用已绑定「 UnionID 机制」时返回。
 */
public record QqIdentity(String openId, String unionId) {
}
