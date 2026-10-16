package com.studenthub.integration.qq;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * mock 模式：不访问 QQ，用 accessToken 的 SHA-256 派生稳定 openid，
 * 同一 accessToken 每次得到同一身份，便于本地联调复用同一账号。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studenthub.qq", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockQqIdentityProvider implements QqIdentityProvider {

    private final QqProperties properties;

    @Override
    public QqIdentity exchange(String accessToken) {
        if (accessToken == null || accessToken.isBlank() || accessToken.startsWith("invalid")) {
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        return new QqIdentity(hash(accessToken), null);
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
