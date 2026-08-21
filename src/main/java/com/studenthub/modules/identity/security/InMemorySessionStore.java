package com.studenthub.modules.identity.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "memory", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, StoredSession> sessions = new ConcurrentHashMap<>();

    @Override
    public SessionToken create(AuthenticatedUser user, Duration ttl) {
        String rawToken = SecureTokenSupport.generate();
        Instant expiresAt = Instant.now().plus(ttl);
        sessions.put(SecureTokenSupport.hash(rawToken), new StoredSession(user, expiresAt));
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
            sessions.remove(tokenHash);
            return Optional.empty();
        }
        return Optional.of(storedSession.user());
    }

    @Override
    public void delete(String rawToken) {
        sessions.remove(SecureTokenSupport.hash(rawToken));
    }

    private record StoredSession(AuthenticatedUser user, Instant expiresAt) {
    }
}
