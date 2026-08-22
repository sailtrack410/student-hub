package com.studenthub.integration.wechat;

public interface WechatIdentityProvider {

    WechatIdentity exchange(String code);

    Long defaultCampusId();
}
