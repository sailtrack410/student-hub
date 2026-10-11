package com.studenthub.modules.identity.security;

import com.studenthub.common.util.Digests;

import java.security.SecureRandom;
import java.util.Base64;

final class SecureTokenSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureTokenSupport() {
    }

    static String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        return Digests.sha256Hex(rawToken);
    }
}
