package com.studenthub.modules.evaluation.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.evaluation.controller.dto.AdminEvaluationPageQuery;
import com.studenthub.modules.evaluation.controller.dto.AdminEvaluationSummaryResponse;
import com.studenthub.modules.evaluation.controller.dto.AdminReplyRequest;
import com.studenthub.modules.evaluation.service.AdminEvaluationService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/evaluations")
@Tag(name = "管理端-服务评价", description = "全平台评价查询与回复")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('catalog:manage')")
public class AdminEvaluationController {

    private final AdminEvaluationService adminEvaluationService;

    @GetMapping
    @Operation(summary = "分页查询服务评价", description = "全平台数据范围，可按评分与回复状态过滤。", operationId = "pageAdminEvaluations")
    public Result<PageResult<AdminEvaluationSummaryResponse>> page(AdminEvaluationPageQuery query) {
        return Result.success(adminEvaluationService.page(query));
    }

    @PostMapping("/{evaluationId}/reply")
    @Operation(summary = "回复服务评价", description = "写入商家回复并站内通知评价人，一条评价仅可回复一次。", operationId = "replyAdminEvaluation")
    public Result<Void> reply(@PathVariable Long evaluationId, @RequestBody @Valid AdminReplyRequest request) {
        adminEvaluationService.reply(evaluationId, request);
        return Result.success();
    }
}
