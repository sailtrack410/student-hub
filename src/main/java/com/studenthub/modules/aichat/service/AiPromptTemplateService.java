package com.studenthub.modules.aichat.service;

import com.studenthub.modules.aichat.controller.dto.AiPromptTemplateRequest;
import com.studenthub.modules.aichat.controller.dto.AiPromptTemplateResponse;

import java.util.List;

/**
 * AI 提示词版本管理：平台级（campus_id=0），激活后即时生效于运行时。
 */
public interface AiPromptTemplateService {

    List<AiPromptTemplateResponse> list(String keyword, String scene, String status);

    AiPromptTemplateResponse create(AiPromptTemplateRequest request);

    AiPromptTemplateResponse update(Long id, AiPromptTemplateRequest request);

    /** 激活某一版本：同 promptCode 的其他版本自动失活，并刷新运行时快照。 */
    AiPromptTemplateResponse activate(Long id);

    /** 停用：若停用的是当前生效版本，运行时回退到代码内置基线。 */
    AiPromptTemplateResponse disable(Long id);

    void delete(Long id);

    /** 重建运行时快照（启动、激活、停用、删除后调用）。 */
    void refreshRuntime();
}
