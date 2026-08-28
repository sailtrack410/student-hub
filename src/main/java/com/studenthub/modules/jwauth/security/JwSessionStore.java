package com.studenthub.modules.jwauth.security;

import java.time.Duration;
import java.util.Optional;

/**
 * 教务系统会话托管：Cookie 与登录落地页地址加密后按用户存储，与平台会话完全分开失效。
 * 落地页地址含访问令牌参数（homes.action?v=...），后续拉取个人信息必须复用。
 */
public interface JwSessionStore {

    void save(Long userId, JwSession session, Duration ttl);

    Optional<JwSession> find(Long userId);

    void delete(Long userId);

    record JwSession(String cookieHeader, String homeUrl) {
    }
}
