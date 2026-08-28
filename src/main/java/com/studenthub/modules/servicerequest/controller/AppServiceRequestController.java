package com.studenthub.modules.servicerequest.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.servicerequest.controller.dto.MyServiceRequestPageQuery;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestCancelRequest;
import com.studenthub.modules.servicerequest.controller.dto.SubmitServiceRequestRequest;
import com.studenthub.modules.servicerequest.service.ServiceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/service-requests")
@Tag(name = "小程序-服务申请")
@RequiredArgsConstructor
public class AppServiceRequestController {

    private final ServiceRequestService serviceRequestService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(summary = "提交服务申请", description = "按服务项表单定义提交申请，返回创建后的申请详情。"
            + "建议携带 Idempotency-Key 请求头防止重复提交，重复提交时返回首次创建的申请。",
            operationId = "submitServiceRequest")
    @PreAuthorize("hasAuthority('service-request:create')")
    public Result<ServiceRequestDetailResponse> submit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid SubmitServiceRequestRequest request) {
        return Result.success(serviceRequestService.submit(
                currentUserProvider.requireCurrentUser(), request, idempotencyKey));
    }

    @GetMapping
    @Operation(summary = "分页查询我的申请", description = "返回当前用户提交的申请，按提交时间倒序，可按状态过滤。",
            operationId = "pageMyServiceRequests")
    @PreAuthorize("hasAuthority('service-request:view:self')")
    public Result<PageResult<ServiceRequestSummaryResponse>> pageMy(MyServiceRequestPageQuery query) {
        return Result.success(serviceRequestService.pageMy(currentUserProvider.requireCurrentUser(),
                query.pageNum(), query.pageSize(), query.status()));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "查询申请详情", description = "仅申请人本人可查看，包含表单内容、工单、评价与状态时间线。",
            operationId = "getServiceRequestDetail")
    @PreAuthorize("hasAuthority('service-request:view:self')")
    public Result<ServiceRequestDetailResponse> detail(@PathVariable Long requestId) {
        return Result.success(serviceRequestService.detail(currentUserProvider.requireCurrentUser(), requestId));
    }

    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "撤销申请", description = "申请办结前可撤销，已派单的工单同步撤销并通知服务人员。",
            operationId = "cancelServiceRequest")
    @PreAuthorize("hasAuthority('service-request:view:self')")
    public Result<ServiceRequestDetailResponse> cancel(@PathVariable Long requestId,
                                                       @RequestBody @Valid ServiceRequestCancelRequest request) {
        return Result.success(serviceRequestService.cancel(
                currentUserProvider.requireCurrentUser(), requestId, request.reason()));
    }
}
