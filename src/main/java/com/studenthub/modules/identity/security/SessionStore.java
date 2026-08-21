package com.studenthub.modules.identity.security;

import java.time.Duration;
import java.util.Optional;

public interface SessionStore {

    SessionToken create(AuthenticatedUser user, Duration ttl);

    Optional<AuthenticatedUser> find(String rawToken);

    void delete(String rawToken);
}
