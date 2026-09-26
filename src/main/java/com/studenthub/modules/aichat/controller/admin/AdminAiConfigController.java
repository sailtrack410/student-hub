package com.studenthub.modules.aichat.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.aichat.controller.dto.AiConfigResponse;
import com.studenthub.modules.aichat.controller.dto.AiConfigUpdateRequest;
import com.studenthub.modules.aichat.service.AiConfigService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 全局设置（总开关 + 超时 + 限流）；供应商/模型管理见 AdminAiProviderController。
 */
@RestController
@RequestMapping("/api/admin/ai/config")
@Tag(name = "管理端-AI 全局设置", description = "AI 总开关、超时与限流配置")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ai:manage')")
public class AdminAiConfigController {

    private final AiConfigService aiConfigService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询 AI 全局设置", operationId = "getAiConfig")
    public Result<AiConfigResponse> current() {
        return Result.success(aiConfigService.current());
    }

    @PutMapping
    @Operation(summary = "保存 AI 全局设置", description = "保存后进程内即时生效，无需重启。",
            operationId = "updateAiConfig")
    public Result<AiConfigResponse> update(@RequestBody @Valid AiConfigUpdateRequest request) {
        return Result.success(aiConfigService.update(currentUserProvider.currentUserIdOrSystem(), request));
    }
}
