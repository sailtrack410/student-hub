package com.studenthub.modules.identity.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "memory", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, StoredSession> sessions = new ConcurrentHashMap<>();

    /** 用户 -> 其会话 token 哈希集合，支撑管理端按用户刷新/下线 */
    private final ConcurrentHashMap<Long, Set<String>> sessionHashesByUser = new ConcurrentHashMap<>();

    @Override
    public SessionToken create(AuthenticatedUser user, Duration ttl) {
        String rawToken = SecureTokenSupport.generate();
        Instant expiresAt = Instant.now().plus(ttl);
        String tokenHash = SecureTokenSupport.hash(rawToken);
        sessions.put(tokenHash, new StoredSession(user, expiresAt));
        sessionHashesByUser
                .computeIfAbsent(user.userId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(tokenHash);
        return new SessionToken(rawToken, expiresAt);
    }

    @Override
    public Optional<AuthenticatedUser> find(String rawToken) {
        String tokenHash = SecureTokenSupport.hash(rawToken);
        StoredSession storedSession = sessions.get(tokenHash);
        if (storedSession == null) {
            return Optional.empty();
        }
        if (storedSession.expiresAt().isBefore(Instant.now())) {
            remove(tokenHash, storedSession.user().userId());
            return Optional.empty();
        }
        return Optional.of(storedSession.user());
    }

    @Override
    public void replace(String rawToken, AuthenticatedUser user) {
        sessions.computeIfPresent(SecureTokenSupport.hash(rawToken),
                (tokenHash, storedSession) -> new StoredSession(user, storedSession.expiresAt()));
    }

    @Override
    public void delete(String rawToken) {
        String tokenHash = SecureTokenSupport.hash(rawToken);
        StoredSession storedSession = sessions.remove(tokenHash);
        if (storedSession != null) {
            removeFromIndex(storedSession.user().userId(), tokenHash);
        }
    }

    @Override
    public boolean renew(String rawToken, Long userId, Duration ttl, Duration threshold) {
        // 内存实现的会话索引随会话对象生命周期管理，无需按 userId 单独续期
        if (ttl == null || ttl.isZero() || ttl.isNegative()
                || threshold == null || threshold.isNegative() || threshold.isZero()) {
            return false;
        }
        String tokenHash = SecureTokenSupport.hash(rawToken);
        StoredSession storedSession = sessions.get(tokenHash);
        Instant now = Instant.now();
        if (storedSession == null || storedSession.expiresAt().isBefore(now)) {
            return false;
        }
        if (Duration.between(now, storedSession.expiresAt()).toSeconds() > threshold.toSeconds()) {
            return false;
        }
        sessions.put(tokenHash, new StoredSession(storedSession.user(), now.plus(ttl)));
        return true;
    }

    @Override
    public int refreshUserSessions(Long userId, AuthenticatedUser refreshed) {
        int[] count = {0};
        for (String tokenHash : sessionHashesByUser.getOrDefault(userId, Set.of())) {
            sessions.computeIfPresent(tokenHash, (ignored, storedSession) -> {
                count[0]++;
                return new StoredSession(refreshed, storedSession.expiresAt());
            });
        }
        sessionHashesByUser.computeIfPresent(userId, (ignored, hashes) -> {
            hashes.removeIf(tokenHash -> !sessions.containsKey(tokenHash));
            return hashes.isEmpty() ? null : hashes;
        });
        return count[0];
    }

    @Override
    public int deleteUserSessions(Long userId) {
        Set<String> hashes = sessionHashesByUser.remove(userId);
        if (hashes == null) {
            return 0;
        }
        int count = 0;
        for (String tokenHash : hashes) {
            if (sessions.remove(tokenHash) != null) {
                count++;
            }
        }
        return count;
    }

    private void remove(String tokenHash, Long userId) {
        sessions.remove(tokenHash);
        removeFromIndex(userId, tokenHash);
    }

    private void removeFromIndex(Long userId, String tokenHash) {
        sessionHashesByUser.computeIfPresent(userId, (ignored, hashes) -> {
            hashes.remove(tokenHash);
            return hashes.isEmpty() ? null : hashes;
        });
    }

    private record StoredSession(AuthenticatedUser user, Instant expiresAt) {
    }
}
