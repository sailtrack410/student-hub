package com.studenthub.modules.push.provider;

import org.springframework.stereotype.Component;

/** FCM（Android/Web）通道：未接入厂商 SDK 前恒返回 NOT_CONFIGURED。 */
@Component
public class FcmPushProvider extends UnconfiguredPushProvider {

    public FcmPushProvider(PushProviderProperties properties) {
        super("FCM", properties.getFcm());
    }

    @Override
    protected String configKey() {
        return "fcm";
    }
}
