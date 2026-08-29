package com.studenthub.modules.aichat.service;

import com.studenthub.modules.aichat.controller.dto.AiDefaultModelResponse;
import com.studenthub.modules.aichat.controller.dto.AiDefaultModelSetRequest;
import com.studenthub.modules.aichat.controller.dto.AiModelImportRequest;
import com.studenthub.modules.aichat.controller.dto.AiModelResponse;
import com.studenthub.modules.aichat.controller.dto.AiModelUpdateRequest;
import com.studenthub.modules.aichat.controller.dto.AiProviderCheckResponse;
import com.studenthub.modules.aichat.controller.dto.AiProviderCreateRequest;
import com.studenthub.modules.aichat.controller.dto.AiProviderResponse;
import com.studenthub.modules.aichat.controller.dto.AiProviderUpdateRequest;

import java.util.List;

/**
 * 管理端 AI 供应商/模型管理：任何变更后都会刷新运行时快照，即时生效。
 */
public interface AiProviderAdminService {

    List<AiProviderResponse> listProviders();

    AiProviderResponse createProvider(Long operatorId, AiProviderCreateRequest request);

    AiProviderResponse updateProvider(Long operatorId, Long providerId, AiProviderUpdateRequest request);

    void deleteProvider(Long operatorId, Long providerId);

    /** 探测供应商 /models 接口，结果落库并返回 */
    AiProviderCheckResponse checkConnectivity(Long providerId);

    /** 拉取供应商可用模型列表（GET /models） */
    List<String> fetchRemoteModels(Long providerId);

    /** 批量导入模型（已存在自动跳过），返回实际新增数量 */
    int importModels(Long operatorId, Long providerId, AiModelImportRequest request);

    List<AiModelResponse> listModels(String keyword, String modelType);

    AiModelResponse updateModel(Long operatorId, Long modelId, AiModelUpdateRequest request);

    void deleteModel(Long operatorId, Long modelId);

    List<AiDefaultModelResponse> listDefaults();

    /** 设置/清除某类型默认模型，返回设置后的该类型视图 */
    AiDefaultModelResponse setDefault(Long operatorId, AiDefaultModelSetRequest request);
}
