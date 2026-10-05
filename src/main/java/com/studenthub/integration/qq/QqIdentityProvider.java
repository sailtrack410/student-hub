package com.studenthub.integration.qq;

public interface QqIdentityProvider {

    /**
     * 用客户端 QQ SDK 授权得到的 accessToken 反查 QQ 侧身份。
     * openId 由服务端从 QQ 反查获得，不信任客户端提交值。
     */
    QqIdentity exchange(String accessToken);

    Long defaultCampusId();
}
