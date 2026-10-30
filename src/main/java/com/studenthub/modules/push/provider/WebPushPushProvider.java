package com.studenthub.modules.push.provider;

import org.springframework.stereotype.Component;

/** Web Push（浏览器）通道：未接入厂商 SDK 前恒返回 NOT_CONFIGURED。 */
@Component
public class WebPushPushProvider extends UnconfiguredPushProvider {

    public WebPushPushProvider(PushProviderProperties properties) {
        super("WEBPUSH", properties.getWebPush());
    }

    @Override
    protected String configKey() {
        return "webpush";
    }
}
