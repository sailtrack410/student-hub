package com.studenthub.modules.jwauth.service;

import com.studenthub.modules.jwauth.controller.dto.JwAuthRequest;
import com.studenthub.modules.jwauth.controller.dto.JwAuthResult;
import com.studenthub.modules.jwauth.controller.dto.JwCaptchaResponse;
import com.studenthub.modules.jwauth.controller.dto.JwProfileResponse;
import com.studenthub.modules.jwauth.controller.dto.JwStatusResponse;

public interface JwAuthService {

    JwCaptchaResponse captcha();

    JwStatusResponse status(Long userId);

    /**
     * 执行教务认证：成功后落绑定、切换平台身份（学生/教师）并托管加密教务会话。
     */
    JwAuthResult auth(Long userId, String rawToken, JwAuthRequest request);

    void unbind(Long userId);

    JwProfileResponse profile(Long userId);
}
