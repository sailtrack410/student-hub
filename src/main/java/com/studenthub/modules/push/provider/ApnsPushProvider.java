package com.studenthub.modules.push.provider;

import org.springframework.stereotype.Component;

/** APNs（iOS）通道：未接入厂商 SDK 前恒返回 NOT_CONFIGURED。 */
@Component
public class ApnsPushProvider extends UnconfiguredPushProvider {

    public ApnsPushProvider(PushProviderProperties properties) {
        super("APNS", properties.getApns());
    }

    @Override
    protected String configKey() {
        return "apns";
    }
}
