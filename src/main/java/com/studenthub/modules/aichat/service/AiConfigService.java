package com.studenthub.modules.aichat.service;

import com.studenthub.integration.ai.AiModelBinding;
import com.studenthub.modules.aichat.controller.dto.AiConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiConfigUpdateRequest;

/**
 * AI 全局设置：管理后台读写，保存后即时生效（进程内快照刷新）。
 * 供应商/模型/默认模型管理见 {@link AiProviderAdminService}。
 */
public interface AiConfigService {

    /** 查询全局设置（总开关 + 超时 + 限流），附带当前生效的默认聊天模型名 */
    AiConfigResponse current();

    /** 保存全局设置并刷新进程内快照，返回保存后的响应 */
    AiConfigResponse update(Long operatorId, AiConfigUpdateRequest request);

    /**
     * 从 ai_provider/ai_model/ai_default_model 重建运行时快照；
     * 启动时与供应商/模型/默认模型任何变更后调用。
     */
    void refreshRuntime();

    /**
     * 按类型码解析默认模型绑定（走 ai_default_model 表）：默认模型存在 → 模型启用 →
     * 供应商启用且已配密钥，任一环缺失返回 null（静默降级语义，调用方回退或报 AI_NOT_CONFIGURED）。
     */
    AiModelBinding resolveBinding(String typeCode);

    /**
     * 显式指定供应商（可选模型）解析绑定，管理端流式测试用：
     * 供应商不存在/未启用抛 NOT_FOUND；未配密钥或该供应商下无启用模型抛 AI_NOT_CONFIGURED
     * （显式指定型抛异常语义，与按类型码解析的静默降级语义分开保留，不做合并）。
     */
    AiModelBinding resolveBinding(Long providerId, String modelId);
}
