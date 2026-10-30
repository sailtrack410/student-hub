package com.studenthub.modules.push.service;

import com.studenthub.modules.push.controller.dto.PushTokenResponse;
import com.studenthub.modules.push.controller.dto.RegisterPushTokenRequest;

public interface PushTokenService {
    PushTokenResponse register(Long userId, RegisterPushTokenRequest request);
    void unregister(Long userId, String token);
}
