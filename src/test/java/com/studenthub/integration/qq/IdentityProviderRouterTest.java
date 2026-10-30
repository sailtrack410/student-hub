package com.studenthub.integration.qq;

import com.studenthub.integration.wechat.MockWechatIdentityProvider;
import com.studenthub.integration.wechat.WechatApiIdentityProvider;
import com.studenthub.integration.wechat.WechatIdentity;
import com.studenthub.integration.wechat.WechatIdentityProviderRouter;
import com.studenthub.integration.wechat.WechatProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 模式切换不应依赖重启：路由器按当前 Properties.mode 选择 mock/real。 */
class IdentityProviderRouterTest {

    @Test
    void qqRouterDelegatesByCurrentMode() {
        QqProperties properties = new QqProperties();
        QqApiIdentityProvider api = mock(QqApiIdentityProvider.class);
        MockQqIdentityProvider mock = mock(MockQqIdentityProvider.class);
        QqIdentityProviderRouter router = new QqIdentityProviderRouter(api, mock, properties);
        QqIdentity apiIdentity = new QqIdentity("openid-api", null, "API", null);
        QqIdentity mockIdentity = new QqIdentity("openid-mock", null, "MOCK", null);
        when(api.exchange("token")).thenReturn(apiIdentity);
        when(mock.exchange("token")).thenReturn(mockIdentity);

        properties.setMode("real");
        assertEquals(apiIdentity, router.exchange("token"));
        verify(api).exchange("token");

        properties.setMode("mock");
        assertEquals(mockIdentity, router.exchange("token"));
        verify(mock).exchange("token");
    }

    @Test
    void wechatRouterDelegatesByCurrentMode() {
        WechatProperties properties = new WechatProperties();
        WechatApiIdentityProvider api = mock(WechatApiIdentityProvider.class);
        MockWechatIdentityProvider mock = mock(MockWechatIdentityProvider.class);
        WechatIdentityProviderRouter router = new WechatIdentityProviderRouter(api, mock, properties);
        WechatIdentity apiIdentity = new WechatIdentity("openid-api");
        WechatIdentity mockIdentity = new WechatIdentity("openid-mock");
        when(api.exchange("code")).thenReturn(apiIdentity);
        when(mock.exchange("code")).thenReturn(mockIdentity);

        properties.setMode("mock");
        assertEquals(mockIdentity, router.exchange("code"));
        verify(mock).exchange("code");
        verifyNoInteractions(api);

        properties.setMode("real");
        assertEquals(apiIdentity, router.exchange("code"));
        verify(api).exchange("code");
    }
}
