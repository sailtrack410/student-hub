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

    /**
     * 原地刷新指定用户的全部在线会话（管理端变更角色/授权后调用），保留各自剩余有效期；
     * 返回刷新的会话数，用户不在线时为 0。
     */
    int refreshUserSessions(Long userId, AuthenticatedUser refreshed);

    /**
     * 删除指定用户的全部在线会话（管理端停用账号后强制下线）；返回删除的会话数。
     */
    int deleteUserSessions(Long userId);
}
