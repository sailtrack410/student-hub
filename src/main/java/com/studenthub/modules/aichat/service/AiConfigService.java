package com.studenthub.modules.aichat.service;

import com.studenthub.modules.aichat.controller.dto.AiConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiConfigUpdateRequest;

/**
 * AI 动态配置：管理后台读写，保存后即时生效（进程内快照刷新）。
 */
public interface AiConfigService {

    /** 查询当前配置（apiKey 脱敏）；DB 无配置时回退环境变量默认值 */
    AiConfigResponse current();

    /** 保存配置并刷新进程内快照，返回保存后的响应 */
    AiConfigResponse update(Long operatorId, AiConfigUpdateRequest request);
}