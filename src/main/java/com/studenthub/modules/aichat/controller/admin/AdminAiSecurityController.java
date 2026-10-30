package com.studenthub.modules.aichat.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.aichat.controller.dto.AiPromptTemplateRequest;
import com.studenthub.modules.aichat.controller.dto.AiPromptTemplateResponse;
import com.studenthub.modules.aichat.controller.dto.AiSecurityConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiSecurityConfigUpdateRequest;
import com.studenthub.modules.aichat.controller.dto.AiSecurityStatusResponse;
import com.studenthub.modules.aichat.service.AiPromptTemplateService;
import com.studenthub.modules.aichat.service.AiSecurityConfigService;
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

/**
 * 管理端-AI 安全（I04）：版本化提示词 + 安全策略（敏感词/工具白名单/限流/配额/内容保留）。
 */
@RestController
@RequestMapping("/api/admin/ai/security")
@Tag(name = "管理端-AI 安全", description = "版本化提示词与 AI 安全策略配置")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('ai:security:manage') or hasAuthority('ai:manage')")
public class AdminAiSecurityController {

    private final AiPromptTemplateService promptTemplateService;
    private final AiSecurityConfigService securityConfigService;
    private final CurrentUserProvider currentUserProvider;

    // ---------------------------------------------------------------- 提示词版本

    @GetMapping("/prompts")
    @Operation(summary = "提示词版本列表", description = "平台级配置，按编码升序、版本倒序；active 表示当前生效版本。",
            operationId = "adminListAiPrompts")
    public Result<List<AiPromptTemplateResponse>> listPrompts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String status) {
        return Result.success(promptTemplateService.list(keyword, scene, status));
    }

    @PostMapping("/prompts")
    @Operation(summary = "新建提示词版本", description = "版本号自动递增；新建版本默认未激活。",
            operationId = "adminCreateAiPrompt")
    public Result<AiPromptTemplateResponse> createPrompt(@RequestBody @Valid AiPromptTemplateRequest request) {
        return Result.success(promptTemplateService.create(request));
    }

    @PutMapping("/prompts/{promptId}")
    @Operation(summary = "更新提示词版本", description = "仅未生效版本可编辑内容；已生效版本请新建版本。",
            operationId = "adminUpdateAiPrompt")
    public Result<AiPromptTemplateResponse> updatePrompt(@PathVariable Long promptId,
                                                         @RequestBody @Valid AiPromptTemplateRequest request) {
        return Result.success(promptTemplateService.update(promptId, request));
    }

    @PostMapping("/prompts/{promptId}/activate")
    @Operation(summary = "激活提示词版本", description = "同编码旧版本自动失活；运行时立即生效。",
            operationId = "adminActivateAiPrompt")
    public Result<AiPromptTemplateResponse> activatePrompt(@PathVariable Long promptId) {
        return Result.success(promptTemplateService.activate(promptId));
    }

    @PostMapping("/prompts/{promptId}/disable")
    @Operation(summary = "停用提示词版本", description = "停用当前生效版本后运行时回退代码内置基线。",
            operationId = "adminDisableAiPrompt")
    public Result<AiPromptTemplateResponse> disablePrompt(@PathVariable Long promptId) {
        return Result.success(promptTemplateService.disable(promptId));
    }

    @DeleteMapping("/prompts/{promptId}")
    @Operation(summary = "删除提示词版本", description = "生效中版本不可删除。",
            operationId = "adminDeleteAiPrompt")
    public Result<Void> deletePrompt(@PathVariable Long promptId) {
        promptTemplateService.delete(promptId);
        return Result.success();
    }

    // ---------------------------------------------------------------- 安全策略

    @GetMapping("/config")
    @Operation(summary = "AI 安全策略配置", operationId = "adminGetAiSecurityConfig")
    public Result<AiSecurityConfigResponse> config() {
        return Result.success(securityConfigService.current());
    }

    @PutMapping("/config")
    @Operation(summary = "保存 AI 安全策略", description = "保存后立即刷新运行时（敏感词/限流/配额）。",
            operationId = "adminUpdateAiSecurityConfig")
    public Result<AiSecurityConfigResponse> updateConfig(@RequestBody @Valid AiSecurityConfigUpdateRequest request) {
        Long operatorId = currentUserProvider.currentUserIdOrSystem();
        return Result.success(securityConfigService.update(operatorId, request));
    }

    @GetMapping("/status")
    @Operation(summary = "AI 安全运行时状态", description = "明确哪些策略已接入运行时、哪些仅配置。",
            operationId = "adminGetAiSecurityStatus")
    public Result<AiSecurityStatusResponse> status() {
        return Result.success(securityConfigService.status());
    }
}
