package com.studenthub.modules.workorder.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.workorder.controller.dto.CompleteWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.WorkOrderDetailResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderPageQuery;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderPhotoRequest;
import com.studenthub.modules.workorder.controller.dto.SubmitWorkOrderCompletionRequest;
import com.studenthub.modules.workorder.controller.dto.RejectWorkOrderCompletionRequest;
import com.studenthub.modules.workorder.service.WorkOrderService;
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
@RequestMapping("/api/app/work-orders")
@Tag(name = "小程序-我的工单")
@RequiredArgsConstructor
public class AppWorkOrderController {

    private final WorkOrderService workOrderService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/assigned")
    @Operation(summary = "分页查询被指派工单", description = "返回指派给当前服务人员的工单，可按状态过滤。",
            operationId = "pageAssignedWorkOrders")
    @PreAuthorize("hasAuthority('work-order:view:assigned')")
    public Result<PageResult<WorkOrderSummaryResponse>> pageAssigned(WorkOrderPageQuery query) {
        return Result.success(workOrderService.pageAssigned(currentUserProvider.requireCurrentUser(), query));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "查询被指派工单详情", description = "仅工单当前处理人可查看，包含申请表单内容。",
            operationId = "getAssignedWorkOrderDetail")
    @PreAuthorize("hasAuthority('work-order:view:assigned')")
    public Result<WorkOrderDetailResponse> detail(@PathVariable Long orderId) {
        return Result.success(workOrderService.detailForWorker(currentUserProvider.requireCurrentUser(), orderId));
    }

    @PostMapping("/{orderId}/start")
    @Operation(summary = "开始处理工单", description = "服务人员开始处理被指派的工单。", operationId = "startWorkOrder")
    @PreAuthorize("hasAuthority('work-order:process')")
    public Result<Void> start(@PathVariable Long orderId) {
        workOrderService.start(currentUserProvider.requireCurrentUser(), orderId);
        return Result.success();
    }

    @PostMapping("/{orderId}/complete")
    @Operation(summary = "办结工单", description = "填写办理结果说明并办结工单，申请同步办结并通知申请人评价。",
            operationId = "completeWorkOrder")
    @PreAuthorize("hasAuthority('work-order:process')")
    public Result<Void> complete(@PathVariable Long orderId,
                                 @RequestBody @Valid CompleteWorkOrderRequest request) {
        workOrderService.complete(currentUserProvider.requireCurrentUser(), orderId, request.remark());
        return Result.success();
    }

    @PostMapping("/{orderId}/arrival-photo")
    @Operation(summary = "上传现场照片", operationId = "recordWorkOrderArrivalPhoto")
    @PreAuthorize("hasAuthority('work-order:process')")
    public Result<Void> arrivalPhoto(@PathVariable Long orderId,
                                     @RequestBody @Valid WorkOrderPhotoRequest request) {
        workOrderService.recordArrivalPhoto(currentUserProvider.requireCurrentUser(), orderId, request.fileId());
        return Result.success();
    }

    @PostMapping("/{orderId}/completion-photo")
    @Operation(summary = "上传完工照片并提交报修人确认", operationId = "submitWorkOrderCompletion")
    @PreAuthorize("hasAuthority('work-order:process')")
    public Result<Void> completionPhoto(@PathVariable Long orderId,
                                         @RequestBody @Valid SubmitWorkOrderCompletionRequest request) {
        workOrderService.submitCompletion(currentUserProvider.requireCurrentUser(), orderId, request);
        return Result.success();
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "报修人确认工单完成", operationId = "confirmWorkOrderCompletion")
    @PreAuthorize("hasAuthority('service-request:view:self')")
    public Result<Void> confirm(@PathVariable Long orderId) {
        workOrderService.confirmCompletion(currentUserProvider.requireCurrentUser(), orderId);
        return Result.success();
    }

    @PostMapping("/{orderId}/reject-confirmation")
    @Operation(summary = "报修人退回工单确认", operationId = "rejectWorkOrderCompletion")
    @PreAuthorize("hasAuthority('service-request:view:self')")
    public Result<Void> rejectConfirmation(@PathVariable Long orderId,
                                            @RequestBody @Valid RejectWorkOrderCompletionRequest request) {
        workOrderService.rejectCompletion(currentUserProvider.requireCurrentUser(), orderId, request.reason());
        return Result.success();
    }
}
