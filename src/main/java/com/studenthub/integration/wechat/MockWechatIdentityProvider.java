package com.studenthub.integration.wechat;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.Digests;
import com.studenthub.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
        return new WechatIdentity(Digests.sha256Hex(code));
    }

    @Override
    public Long defaultCampusId() {
        return properties.getDefaultCampusId();
    }
}
