package com.studenthub.modules.workorder.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.workorder.controller.dto.AssignWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.BatchAssignWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.BatchAssignWorkOrderResponse;
import com.studenthub.modules.workorder.controller.dto.ReassignWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.WorkOrderDetailResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderPageQuery;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSlaStatsResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.controller.dto.WorkloadItemResponse;
import com.studenthub.modules.workorder.controller.dto.SubmitWorkOrderCompletionRequest;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;

import java.util.List;

public interface WorkOrderService {

    /**
     * 申请提交后创建待派发工单（同一事务内由申请模块调用）。
     */
    WorkOrderEntity createPendingForRequest(ServiceRequestEntity request);

    void assign(AuthenticatedUser operator, Long orderId, AssignWorkOrderRequest request);

    /**
     * 改派：把已派发/处理中的工单转移给另一位服务人员，记录时间线并通知新旧处理人。
     */
    void reassign(AuthenticatedUser operator, Long orderId, ReassignWorkOrderRequest request);

    /**
     * 批量派单：仅对「待派发」工单生效，逐条返回失败原因，单条失败不影响其余工单。
     */
    BatchAssignWorkOrderResponse batchAssign(AuthenticatedUser operator, BatchAssignWorkOrderRequest request);

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

    /** 服务人员负载（调度员数据范围内），供派单页负载卡片与选择处理人参考。 */
    List<WorkloadItemResponse> workload(AuthenticatedUser dispatcher);

    /** 工单 SLA 统计（待派发/超时/即将超时/平均处理时长），供调度看板。 */
    WorkOrderSlaStatsResponse slaStats(AuthenticatedUser dispatcher);
}
