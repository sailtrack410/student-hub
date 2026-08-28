package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

public interface IdentityAuthService {

    LoginResponse login(String username, String password);

    LoginResponse wechatLogin(String code);

    /**
     * 开发测试专用：使用固定 openid 登录，首次自动建号，之后复用同一账号，便于本地联调。
     */
    LoginResponse devWechatLogin();

    void logout(String rawToken);

    CurrentUserResponse currentUser();

    /**
     * 更新当前用户昵称，并原地刷新服务端会话中的用户数据。
     */
    CurrentUserResponse updateProfile(String rawToken, AuthenticatedUser current, String displayName);

    /**
     * 设置本人身份标识（仅允许学生/教师自行申报），并原地刷新服务端会话。
     */
    CurrentUserResponse updateIdentityType(String rawToken, AuthenticatedUser current, String identityType);
}
