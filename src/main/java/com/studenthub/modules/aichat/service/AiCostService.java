package com.studenthub.modules.aichat.service;

import com.studenthub.modules.aichat.controller.dto.AiCostOverviewResponse;
import com.studenthub.modules.aichat.controller.dto.AiModelPricingRequest;

/**
 * AI 成本看板：聚合 ai_message 的历史 token 用量，按 ai_model 配置的单价估算成本。
 * 未配置单价的模型成本返回 null，绝不推算/伪造金额；不调用真实模型 API。
 */
public interface AiCostService {

    AiCostOverviewResponse overview(int days);

    /** 设置/清除模型单价（每 1K tokens）；写操作由管理端审计 AOP 记录。 */
    AiCostOverviewResponse.ModelPricing updatePricing(Long operatorId, Long modelId, AiModelPricingRequest request);
}
