package com.studenthub.integration.qq;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.Digests;
import com.studenthub.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * mock 模式：不访问 QQ，用 accessToken 的 SHA-256 派生稳定 openid，
 * 同一 accessToken 每次得到同一身份，便于本地联调复用同一账号。
 */
@Component
@RequiredArgsConstructor
public class MockQqIdentityProvider implements QqIdentityProvider {

    private final QqProperties properties;

    @Override
    public QqIdentity exchange(String accessToken) {
        if (accessToken == null || accessToken.isBlank() || accessToken.startsWith("invalid")) {
            throw new BusinessException(ErrorCode.QQ_LOGIN_FAILED);
        }
        String openId = Digests.sha256Hex(accessToken);
        // mock 模式也返回稳定的昵称，便于本地确认「QQ 昵称默认展示」；头像为空由 App 显示默认头像。
        return new QqIdentity(openId, null, "QQ用户" + openId.substring(0, 4), null);
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}
