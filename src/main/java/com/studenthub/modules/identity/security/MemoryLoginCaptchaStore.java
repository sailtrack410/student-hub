package com.studenthub.modules.identity.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "studenthub.security", name = "session-store", havingValue = "memory", matchIfMissing = true)
public class MemoryLoginCaptchaStore implements LoginCaptchaStore {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private record Entry(String answer, Instant expires) {}
    @Override
    public synchronized void put(String id, String answer, Duration ttl) {
        entries.entrySet().removeIf(e -> !e.getValue().expires().isAfter(Instant.now()));
        if (entries.size() >= 10_000) entries.keySet().stream().findFirst().ifPresent(entries::remove);
        entries.put(id, new Entry(answer, Instant.now().plus(ttl)));
    }
    @Override
    public String consume(String id) {
        Entry entry = entries.remove(id);
        return entry != null && entry.expires().isAfter(Instant.now()) ? entry.answer() : null;
    }
}
