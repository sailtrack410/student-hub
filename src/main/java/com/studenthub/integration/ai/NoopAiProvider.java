package com.studenthub.integration.ai;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "studenthub.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopAiProvider implements AiProvider {

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
    }
}
