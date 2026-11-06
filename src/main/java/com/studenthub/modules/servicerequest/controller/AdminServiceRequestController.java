package com.studenthub.modules.servicerequest.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.servicerequest.controller.dto.AdminReviewRequest;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestPageQuery;
import com.studenthub.modules.servicerequest.controller.dto.AdminServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.service.AdminServiceRequestService;
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
@RequestMapping("/api/admin/service-requests")
@Tag(name = "管理端-服务申请审批", description = "服务申请分页与审批")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('catalog:manage') or hasAuthority('work-order:dispatch')")
public class AdminServiceRequestController {

    private final AdminServiceRequestService adminServiceRequestService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "分页查询服务申请", description = "全平台数据范围，可按状态过滤。", operationId = "pageAdminServiceRequests")
    public Result<PageResult<AdminServiceRequestSummaryResponse>> page(AdminServiceRequestPageQuery query) {
        return Result.success(adminServiceRequestService.page(query));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "查询审批详情",
            description = "返回申请人（脱敏手机号）、申请表单与图片材料、工单 SLA/照片、评价和状态时间线。",
            operationId = "getAdminServiceRequestDetail")
    public Result<AdminServiceRequestDetailResponse> detail(@PathVariable Long requestId) {
        return Result.success(adminServiceRequestService.detail(requestId));
    }

    @PostMapping("/{requestId}/complete")
    @Operation(summary = "人工受理事项办结", description = "仅 MANUAL 处理中事项可执行，不支持维修或请假。", operationId = "completeManualServiceRequest")
    public Result<Void> complete(@PathVariable Long requestId,
            @RequestBody(required = false) @Valid com.studenthub.modules.servicerequest.controller.dto.AdminLeaveActionRequest request) {
        adminServiceRequestService.complete(currentUserProvider.requireCurrentUser(), requestId,
                request == null ? null : request.comment());
        return Result.success();
    }

    @PostMapping("/{requestId}/review")
    @Operation(summary = "审批服务申请", description = "MANUAL：APPROVE 人工受理 / REJECT 驳回；LEAVE_APPROVAL 复用请假审批；REPAIR 必须走维修派工接口。",
            operationId = "reviewAdminServiceRequest")
    public Result<Void> review(@PathVariable Long requestId, @RequestBody @Valid AdminReviewRequest request) {
        adminServiceRequestService.review(currentUserProvider.requireCurrentUser(), requestId, request);
        return Result.success();
    }
}
