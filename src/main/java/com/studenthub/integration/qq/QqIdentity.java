package com.studenthub.integration.qq;

/**
 * QQ 互联换取的用户身份。unionId 来自 /oauth2.0/me?unionid=1 反查，可能为空：
 * 仅当 QQ 互联应用已绑定「 UnionID 机制」时返回。
 * nickname/avatarUrl 来自 /user/get_user_info；部分授权场景可能为空，调用方需保留默认值。
 */
public record QqIdentity(String openId, String unionId, String nickname, String avatarUrl) {

    /** 兼容只关心 openId/unionId 的调用方（如测试或后续扩展）。 */
    public QqIdentity(String openId, String unionId) {
        this(openId, unionId, null, null);
    }
}
