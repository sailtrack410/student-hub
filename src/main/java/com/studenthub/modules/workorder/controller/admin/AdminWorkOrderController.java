package com.studenthub.modules.workorder.controller.admin;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.workorder.controller.dto.AssignWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.WorkOrderDetailResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderPageQuery;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.controller.dto.RejectWorkOrderRequest;
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
@RequestMapping("/api/admin/work-orders")
@Tag(name = "管理端-工单调度", description = "工单队列查询、派发与驳回")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('work-order:dispatch')")
public class AdminWorkOrderController {

    private final WorkOrderService workOrderService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "分页查询调度队列", description = "按调度员组织数据范围返回工单，可按状态过滤。",
            operationId = "pageWorkOrderQueue")
    public Result<PageResult<WorkOrderSummaryResponse>> pageQueue(WorkOrderPageQuery query) {
        return Result.success(workOrderService.pageQueue(currentUserProvider.requireCurrentUser(), query));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "查询工单详情", description = "返回工单与关联申请的表单内容，需在调度员数据范围内。",
            operationId = "getWorkOrderDetailForDispatcher")
    public Result<WorkOrderDetailResponse> detail(@PathVariable Long orderId) {
        return Result.success(workOrderService.detailForDispatcher(currentUserProvider.requireCurrentUser(), orderId));
    }

    @PostMapping("/{orderId}/assign")
    @Operation(summary = "派发工单", description = "将工单指派给服务人员；首次派单会把申请推进到处理中。",
            operationId = "assignWorkOrder")
    public Result<Void> assign(@PathVariable Long orderId, @RequestBody @Valid AssignWorkOrderRequest request) {
        workOrderService.assign(currentUserProvider.requireCurrentUser(), orderId, request);
        return Result.success();
    }

    @PostMapping("/{orderId}/reject")
    @Operation(summary = "驳回工单", description = "驳回无效或无法受理的工单，申请同步置为未受理并通知申请人。",
            operationId = "rejectWorkOrder")
    public Result<Void> reject(@PathVariable Long orderId, @RequestBody @Valid RejectWorkOrderRequest request) {
        workOrderService.reject(currentUserProvider.requireCurrentUser(), orderId, request.reason());
        return Result.success();
    }
}
