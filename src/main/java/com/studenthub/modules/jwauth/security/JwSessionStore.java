package com.studenthub.modules.jwauth.security;

import java.time.Duration;
import java.util.Optional;

/**
 * 教务系统会话托管：Cookie 头加密后按用户存储，与平台登录会话完全分开失效。
 */
public interface JwSessionStore {

    void save(Long userId, String cookieHeader, Duration ttl);

    Optional<String> find(Long userId);

    void delete(Long userId);
}
