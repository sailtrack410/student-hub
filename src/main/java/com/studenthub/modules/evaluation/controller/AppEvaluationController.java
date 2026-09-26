package com.studenthub.modules.evaluation.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.evaluation.controller.dto.CreateEvaluationRequest;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import com.studenthub.modules.evaluation.service.EvaluationService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/service-requests")
@Tag(name = "小程序-服务评价", description = "申请办结后的服务评价")
@RequiredArgsConstructor
public class AppEvaluationController {

    private final EvaluationService evaluationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/{requestId}/evaluation")
    @Operation(summary = "提交服务评价", description = "已办结申请的申请人可评价一次，评分 1-5 星。",
            operationId = "createServiceEvaluation")
    @PreAuthorize("hasAuthority('evaluation:create')")
    public Result<EvaluationResponse> submit(@PathVariable Long requestId,
                                             @RequestBody @Valid CreateEvaluationRequest request) {
        return Result.success(evaluationService.submit(
                currentUserProvider.requireCurrentUser(), requestId, request));
    }
}
