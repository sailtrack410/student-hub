package com.studenthub.modules.workorder.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.workorder.controller.dto.AssignWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.WorkOrderDetailResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderPageQuery;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.controller.dto.SubmitWorkOrderCompletionRequest;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;

public interface WorkOrderService {

    /**
     * 申请提交后创建待派发工单（同一事务内由申请模块调用）。
     */
    WorkOrderEntity createPendingForRequest(ServiceRequestEntity request);

    void assign(AuthenticatedUser operator, Long orderId, AssignWorkOrderRequest request);

    void reject(AuthenticatedUser operator, Long orderId, String reason);

    void start(AuthenticatedUser worker, Long orderId);

    void complete(AuthenticatedUser worker, Long orderId, String remark);

    void recordArrivalPhoto(AuthenticatedUser worker, Long orderId, Long fileId);

    void submitCompletion(AuthenticatedUser worker, Long orderId, SubmitWorkOrderCompletionRequest request);

    void confirmCompletion(AuthenticatedUser requester, Long orderId);

    void rejectCompletion(AuthenticatedUser requester, Long orderId, String reason);

    /**
     * 申请人撤销申请时联动撤销工单；工单不存在或已终态时静默跳过撤销动作。
     */
    void cancelForRequest(ServiceRequestEntity request, AuthenticatedUser operator, String reason);

    PageResult<WorkOrderSummaryResponse> pageQueue(AuthenticatedUser dispatcher, WorkOrderPageQuery query);

    PageResult<WorkOrderSummaryResponse> pageAssigned(AuthenticatedUser worker, WorkOrderPageQuery query);

    WorkOrderDetailResponse detailForDispatcher(AuthenticatedUser dispatcher, Long orderId);

    WorkOrderDetailResponse detailForWorker(AuthenticatedUser worker, Long orderId);
}
