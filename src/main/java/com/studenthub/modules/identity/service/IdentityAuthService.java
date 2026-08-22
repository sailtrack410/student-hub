package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.LoginResponse;

public interface IdentityAuthService {

    LoginResponse login(String username, String password);

    LoginResponse wechatLogin(String code);

    void logout(String rawToken);

    CurrentUserResponse currentUser();
}
