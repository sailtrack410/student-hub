package com.studenthub.modules.servicerequest.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveActionRequest;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeavePageQuery;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveRejectRequest;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveStatsResponse;
import com.studenthub.modules.servicerequest.controller.dto.AdminLeaveSummaryResponse;
import com.studenthub.modules.servicerequest.service.LeaveRequestService;
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

/**
 * 独立请假审批（H01）：数据来自 service_request 中 item_code = AFFAIR-LEAVE 的申请。
 * 通过 = SUBMITTED→PROCESSING；驳回 = →REJECTED；销假 = PROCESSING→COMPLETED 并写 leave_cancel_at。
 */
@RestController
@RequestMapping("/api/admin/leave-requests")
@Tag(name = "管理端-请假审批", description = "请假申请列表、统计、详情与审批/销假")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('catalog:manage') or hasAuthority('work-order:dispatch')")
public class AdminLeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "请假申请分页", description = "按当前管理端校区与数据范围过滤；status=PENDING/APPROVED/REJECTED/CANCELLED。",
            operationId = "adminPageLeaveRequests")
    public Result<PageResult<AdminLeaveSummaryResponse>> page(AdminLeavePageQuery query) {
        return Result.success(leaveRequestService.page(query));
    }

    @GetMapping("/stats")
    @Operation(summary = "请假审批统计", description = "待审批/已通过/已驳回/已销假数量（当前校区与数据范围）。",
            operationId = "adminGetLeaveRequestStats")
    public Result<AdminLeaveStatsResponse> stats() {
        return Result.success(leaveRequestService.stats());
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "请假审批详情", description = "返回请假人（手机号脱敏）、请假类型/起止/天数/事由、材料、时间线与可执行动作。",
            operationId = "adminGetLeaveRequestDetail")
    public Result<AdminLeaveDetailResponse> detail(@PathVariable Long requestId) {
        return Result.success(leaveRequestService.detail(requestId));
    }

    @PostMapping("/{requestId}/approve")
    @Operation(summary = "通过请假", description = "仅待审批（SUBMITTED）可执行；通过后进入 PROCESSING（请假生效）。",
            operationId = "adminApproveLeaveRequest")
    public Result<Void> approve(@PathVariable Long requestId,
                                @RequestBody(required = false) @Valid AdminLeaveActionRequest request) {
        leaveRequestService.approve(currentUserProvider.requireCurrentUser(), requestId,
                request == null ? null : request.comment());
        return Result.success();
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "驳回请假", description = "驳回原因必填；待审批/处理中可驳回。",
            operationId = "adminRejectLeaveRequest")
    public Result<Void> reject(@PathVariable Long requestId,
                               @RequestBody @Valid AdminLeaveRejectRequest request) {
        leaveRequestService.reject(currentUserProvider.requireCurrentUser(), requestId, request.reason());
        return Result.success();
    }

    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "销假", description = "仅已通过且未销假的请假可执行；办结后 status=COMPLETED，leave_cancel_at 记录销假时间。",
            operationId = "adminCancelLeaveRequest")
    public Result<Void> cancel(@PathVariable Long requestId,
                               @RequestBody(required = false) @Valid AdminLeaveActionRequest request) {
        leaveRequestService.cancelLeave(currentUserProvider.requireCurrentUser(), requestId,
                request == null ? null : request.comment());
        return Result.success();
    }
}
