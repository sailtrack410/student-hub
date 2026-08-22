package com.studenthub.integration.wechat;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "studenthub.wechat", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWechatIdentityProvider implements WechatIdentityProvider {

    private final WechatProperties properties;

    @Override
    public WechatIdentity exchange(String code) {
        if (code == null || code.isBlank() || code.startsWith("invalid")) {
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
        return new WechatIdentity(hash(code));
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
