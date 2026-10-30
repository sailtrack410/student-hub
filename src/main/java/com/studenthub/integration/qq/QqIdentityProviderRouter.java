package com.studenthub.integration.qq;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * QQ 身份提供者路由器：两个实现始终注册为 Bean，按管理端可热更新的
 * {@code studenthub.qq.mode} 选择 mock / real，避免模式切换必须重启。
 */
@Component
@Primary
@RequiredArgsConstructor
public class QqIdentityProviderRouter implements QqIdentityProvider {

    private final QqApiIdentityProvider apiProvider;
    private final MockQqIdentityProvider mockProvider;
    private final QqProperties properties;

    @Override
    public QqIdentity exchange(String accessToken) {
        return "real".equalsIgnoreCase(properties.getMode())
                ? apiProvider.exchange(accessToken)
                : mockProvider.exchange(accessToken);
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}
