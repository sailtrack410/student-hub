package com.studenthub.modules.aichat.controller.admin;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiCostOverviewResponse;
import com.studenthub.modules.aichat.controller.dto.AiModelPricingRequest;
import com.studenthub.modules.aichat.service.AiCostService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 成本看板（I01）：只聚合 ai_message 历史 token，按 ai_model 单价估算；不访问模型 API。
 */
@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "管理端-AI 成本", description = "AI token 用量、模型单价与估算成本")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('ai:manage')")
public class AdminAiCostController {

    private final AiCostService aiCostService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/usage/cost")
    @Operation(summary = "AI 成本总览",
            description = "按当前校区聚合 assistant 消息的 prompt/completion tokens 与请求数；按 ai_model 单价估算成本，未配置单价时成本为 null（pricingConfigured=false）。",
            operationId = "adminGetAiCostOverview")
    public Result<AiCostOverviewResponse> overview(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "days 仅支持 7 或 30");
        }
        return Result.success(aiCostService.overview(days));
    }

    @PostMapping("/models/{modelId}/pricing")
    @Operation(summary = "设置模型单价",
            description = "每 1K tokens 的输入/输出单价；两项均传 null 表示清除，成本将返回 null。写操作自动审计。",
            operationId = "adminUpdateAiModelPricing")
    public Result<AiCostOverviewResponse.ModelPricing> updatePricing(
            @PathVariable Long modelId,
            @RequestBody @Valid AiModelPricingRequest request) {
        return Result.success(aiCostService.updatePricing(
                currentUserProvider.currentUserIdOrSystem(), modelId, request));
    }
}
