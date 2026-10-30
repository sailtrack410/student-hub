package com.studenthub.integration.wechat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 微信身份提供者路由器：两个实现始终注册为 Bean，按管理端可热更新的
 * {@code studenthub.wechat.mode} 选择 mock / real，避免模式切换必须重启。
 */
@Component
@Primary
@RequiredArgsConstructor
public class WechatIdentityProviderRouter implements WechatIdentityProvider {

    private final WechatApiIdentityProvider apiProvider;
    private final MockWechatIdentityProvider mockProvider;
    private final WechatProperties properties;

    @Override
    public WechatIdentity exchange(String code) {
        return "real".equalsIgnoreCase(properties.getMode())
                ? apiProvider.exchange(code)
                : mockProvider.exchange(code);
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}
