package com.studenthub.modules.aichat.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.aichat.controller.dto.AiDefaultModelResponse;
import com.studenthub.modules.aichat.controller.dto.AiDefaultModelSetRequest;
import com.studenthub.modules.aichat.controller.dto.AiModelImportRequest;
import com.studenthub.modules.aichat.controller.dto.AiModelResponse;
import com.studenthub.modules.aichat.controller.dto.AiModelUpdateRequest;
import com.studenthub.modules.aichat.controller.dto.AiProviderCheckResponse;
import com.studenthub.modules.aichat.controller.dto.AiProviderCreateRequest;
import com.studenthub.modules.aichat.controller.dto.AiProviderResponse;
import com.studenthub.modules.aichat.controller.dto.AiProviderUpdateRequest;
import com.studenthub.modules.aichat.service.AiProviderAdminService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "管理端-AI 供应商与模型", description = "AI 供应商、模型与类型默认绑定管理")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ai:manage')")
public class AdminAiProviderController {

    private final AiProviderAdminService aiProviderAdminService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/providers")
    @Operation(summary = "供应商列表", operationId = "adminListAiProviders")
    public Result<List<AiProviderResponse>> providers() {
        return Result.success(aiProviderAdminService.listProviders());
    }

    @PostMapping("/providers")
    @Operation(summary = "新增供应商", operationId = "adminCreateAiProvider")
    public Result<AiProviderResponse> createProvider(@RequestBody @Valid AiProviderCreateRequest request) {
        return Result.success(aiProviderAdminService.createProvider(
                currentUserProvider.currentUserIdOrSystem(), request));
    }

    @PutMapping("/providers/{providerId}")
    @Operation(summary = "编辑供应商", description = "API Key 留空表示保持原值；改动即时生效。",
            operationId = "adminUpdateAiProvider")
    public Result<AiProviderResponse> updateProvider(@PathVariable Long providerId,
                                                     @RequestBody @Valid AiProviderUpdateRequest request) {
        return Result.success(aiProviderAdminService.updateProvider(
                currentUserProvider.currentUserIdOrSystem(), providerId, request));
    }

    @DeleteMapping("/providers/{providerId}")
    @Operation(summary = "删除供应商", description = "级联删除其模型并清除相关默认模型。",
            operationId = "adminDeleteAiProvider")
    public Result<Void> deleteProvider(@PathVariable Long providerId) {
        aiProviderAdminService.deleteProvider(currentUserProvider.currentUserIdOrSystem(), providerId);
        return Result.success();
    }

    @PostMapping("/providers/{providerId}/check")
    @Operation(summary = "检查连通性", description = "探测供应商 /models 接口并记录检查结果。",
            operationId = "adminCheckAiProvider")
    public Result<AiProviderCheckResponse> check(@PathVariable Long providerId) {
        return Result.success(aiProviderAdminService.checkConnectivity(providerId));
    }

    @GetMapping("/providers/{providerId}/remote-models")
    @Operation(summary = "拉取供应商可用模型", description = "调用供应商 GET /models 返回模型 ID 列表。",
            operationId = "adminFetchAiRemoteModels")
    public Result<List<String>> remoteModels(@PathVariable Long providerId) {
        return Result.success(aiProviderAdminService.fetchRemoteModels(providerId));
    }

    @PostMapping("/providers/{providerId}/models/import")
    @Operation(summary = "批量导入模型", description = "已存在的模型自动跳过，返回实际新增数量。",
            operationId = "adminImportAiModels")
    public Result<Integer> importModels(@PathVariable Long providerId,
                                        @RequestBody @Valid AiModelImportRequest request) {
        return Result.success(aiProviderAdminService.importModels(
                currentUserProvider.currentUserIdOrSystem(), providerId, request));
    }

    @GetMapping("/models")
    @Operation(summary = "模型列表（跨供应商）", description = "支持关键词与类型过滤，条目标注所属供应商。",
            operationId = "adminListAiModels")
    public Result<List<AiModelResponse>> models(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "modelType", required = false) String modelType) {
        return Result.success(aiProviderAdminService.listModels(keyword, modelType));
    }

    @PutMapping("/models/{modelId}")
    @Operation(summary = "更新模型", description = "切换启用状态或模型类型，即时生效。",
            operationId = "adminUpdateAiModel")
    public Result<AiModelResponse> updateModel(@PathVariable Long modelId,
                                               @RequestBody @Valid AiModelUpdateRequest request) {
        return Result.success(aiProviderAdminService.updateModel(
                currentUserProvider.currentUserIdOrSystem(), modelId, request));
    }

    @DeleteMapping("/models/{modelId}")
    @Operation(summary = "删除模型", description = "若被设为类型默认模型则一并清除默认。",
            operationId = "adminDeleteAiModel")
    public Result<Void> deleteModel(@PathVariable Long modelId) {
        aiProviderAdminService.deleteModel(currentUserProvider.currentUserIdOrSystem(), modelId);
        return Result.success();
    }

    @GetMapping("/defaults")
    @Operation(summary = "查询类型默认模型", description = "返回 CHAT/VISION/ASR/EMBEDDING 默认模型设置；"
            + "EMBEDDING 会返回知识库向量锁定状态。", operationId = "adminListAiDefaults")
    public Result<List<AiDefaultModelResponse>> defaults() {
        return Result.success(aiProviderAdminService.listDefaults());
    }

    @PutMapping("/defaults")
    @Operation(summary = "设置类型默认模型", description = "modelId 为空表示清除该类型默认；"
            + "知识库已有向量时禁止更换/清除 EMBEDDING 默认模型。", operationId = "adminSetAiDefault")
    public Result<AiDefaultModelResponse> setDefault(@RequestBody @Valid AiDefaultModelSetRequest request) {
        return Result.success(aiProviderAdminService.setDefault(
                currentUserProvider.currentUserIdOrSystem(), request));
    }
}
