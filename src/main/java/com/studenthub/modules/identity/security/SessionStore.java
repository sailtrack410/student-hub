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
     * 滑动续期：当剩余有效期低于 {@code threshold} 时把会话刷新回完整 {@code ttl}。
     * 用于 App 端长期免登录——活跃用户不因 TTL 到期被迫重新登录。
     *
     * @param userId 会话所属用户，用于同步续期用户会话索引（索引先过期会导致按用户下线漏掉在线会话）
     * @return 实际发生续期返回 true；会话不存在、已过期或剩余时间仍充足返回 false
     */
    boolean renew(String rawToken, Long userId, Duration ttl, Duration threshold);

    /**
     * 原地刷新指定用户的全部在线会话（管理端变更角色/授权后调用），保留各自剩余有效期；
     * 返回刷新的会话数，用户不在线时为 0。
     */
    int refreshUserSessions(Long userId, AuthenticatedUser refreshed);

    /**
     * 删除指定用户的全部在线会话（管理端停用账号后强制下线）；返回删除的会话数。
     */
    int deleteUserSessions(Long userId);

    /**
     * 按会话摘要删除单个会话（设备管理踢线用，API需求清单 3.2）；
     * 会话不存在时静默返回。默认空实现兼容未升级的存储（如测试用 InMemory）。
     */
    default void deleteSessionByHash(String tokenHash) {
    }
}
