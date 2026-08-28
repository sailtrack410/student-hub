package com.studenthub.modules.identity.security;

import java.time.Duration;
import java.util.Optional;

public interface SessionStore {

    SessionToken create(AuthenticatedUser user, Duration ttl);

    Optional<AuthenticatedUser> find(String rawToken);

    /**
     * 会话存在时原地替换用户数据并保留剩余有效期；会话不存在则静默忽略。
     */
    void replace(String rawToken, AuthenticatedUser user);

    void delete(String rawToken);
}
