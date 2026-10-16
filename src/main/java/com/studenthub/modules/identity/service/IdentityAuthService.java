package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.CurrentUserResponse;
import com.studenthub.modules.identity.controller.dto.LoginResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

public interface IdentityAuthService {

    LoginResponse login(String username, String password);

    LoginResponse wechatLogin(String code);

    /**
     * QQ 登录：用客户端 QQ SDK 授权的 accessToken 反查 QQ 身份后登录，
     * 首次登录自动创建学生基础账号；openId 仅作反查结果一致性比对，不作身份依据。
     */
    LoginResponse qqLogin(String accessToken, String openId);

    /**
     * 开发测试专用：使用固定 openid 登录，首次自动建号，之后复用同一账号，便于本地联调。
     */
    LoginResponse devWechatLogin();

    void logout(String rawToken);

    CurrentUserResponse currentUser();

    /**
     * 更新当前用户昵称/头像，并原地刷新服务端会话中的用户数据。
     */
    CurrentUserResponse updateProfile(String rawToken, AuthenticatedUser current, String displayName,
                                      Long avatarFileId);

    /**
     * 手动填写联系电话并刷新会话。
     */
    CurrentUserResponse updatePhone(String rawToken, AuthenticatedUser current, String phone);

    /**
     * 用微信 getPhoneNumber 的 code 换取绑定手机号并刷新会话。
     */
    CurrentUserResponse updatePhoneByWechat(String rawToken, AuthenticatedUser current, String jsCode);
}
