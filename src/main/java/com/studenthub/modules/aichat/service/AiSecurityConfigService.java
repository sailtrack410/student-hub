package com.studenthub.modules.aichat.service;

import com.studenthub.modules.aichat.controller.dto.AiSecurityConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiSecurityConfigUpdateRequest;
import com.studenthub.modules.aichat.controller.dto.AiSecurityStatusResponse;

/**
 * AI 安全策略配置：平台级单行配置，保存后即时刷新运行时（限流/敏感词）。
 */
public interface AiSecurityConfigService {

    AiSecurityConfigResponse current();

    AiSecurityConfigResponse update(Long operatorId, AiSecurityConfigUpdateRequest request);

    /** 运行时生效状态：明确哪些策略已接入、哪些仅配置。 */
    AiSecurityStatusResponse status();

    /** 重建运行时快照（启动与保存后调用）。 */
    void refreshRuntime();
}
