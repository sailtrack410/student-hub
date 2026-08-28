package com.studenthub.modules.workorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import com.studenthub.modules.workorder.controller.dto.AssignWorkOrderRequest;
import com.studenthub.modules.workorder.controller.dto.WorkOrderDetailResponse;
import com.studenthub.modules.workorder.controller.dto.WorkOrderPageQuery;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import com.studenthub.modules.workorder.service.WorkOrderService;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String ROLE_WORKER = "WORKER";

    private final WorkOrderMapper workOrderMapper;
    private final ServiceRequestMapper serviceRequestMapper;
    private final UserMapper userMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper serviceItemMapper;
    private final com.studenthub.modules.file.service.FileStorageService fileStorageService;
    private final RequestLogRecorder requestLogRecorder;
    private final NotificationService notificationService;
    private final DispatcherScopeResolver dispatcherScopeResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderEntity createPendingForRequest(ServiceRequestEntity request) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo("WO-" + LocalDate.now().format(ORDER_NO_DATE) + "-TMP");
        order.setRequestId(request.getId());
        order.setCampusId(request.getCampusId());
        order.setItemId(request.getItemId());
        order.setTitle(request.getTitle());
        order.setAssignedOrganizationId(request.getHandlerOrganizationId());
        order.setStatus(WorkOrderTransitions.Status.PENDING_DISPATCH.name());
        order.setPriority(PRIORITY_NORMAL);
        workOrderMapper.insert(order);

        WorkOrderEntity orderNoUpdate = new WorkOrderEntity();
        orderNoUpdate.setId(order.getId());
        orderNoUpdate.setOrderNo("WO-" + LocalDate.now().format(ORDER_NO_DATE) + "-" + order.getId());
        orderNoUpdate.setVersion(order.getVersion());
        workOrderMapper.updateById(orderNoUpdate);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(AuthenticatedUser operator, Long orderId, AssignWorkOrderRequest request) {
        WorkOrderEntity order = requireOrder(orderId);
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.ASSIGN, from);

        UserEntity assignee = userMapper.selectById(request.assigneeId());
        if (assignee == null || !"ENABLED".equals(assignee.getStatus())
                || !hasWorkerRole(request.assigneeId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "被指派人不是在岗服务人员");
        }

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setAssigneeId(assignee.getId());
        update.setAssigneeName(assignee.getDisplayName());
        update.setStatus(to.name());
        update.setAssignedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        workOrderMapper.updateById(update);

        TransitionResult transition = transitionRequest(order.getRequestId(),
                RequestTransitions.Action.DISPATCH, RequestTransitions.Status.PROCESSING);

        requestLogRecorder.record(order.getRequestId(), order.getId(), RequestTransitions.Action.DISPATCH,
                transition.from(), transition.to(), operator.userId(), operator.displayName(), request.comment());

        notificationService.send(new NotificationSendCommand(assignee.getId(), "WORK_ORDER",
                "新工单待处理", "你有一个工单「" + order.getTitle() + "」待开始处理。",
                "WORK_ORDER", order.getId()));
        if (transition.changed() && transition.request() != null) {
            notificationService.send(new NotificationSendCommand(transition.request().getRequesterId(), "REQUEST",
                    "申请处理中", "你的申请「" + order.getTitle() + "」已派单，服务人员将尽快处理。",
                    "SERVICE_REQUEST", order.getRequestId()));
        }
        log.info("工单已派发, orderId={}, assigneeId={}, operatorId={}", orderId, assignee.getId(), operator.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(AuthenticatedUser operator, Long orderId, String reason) {
        WorkOrderEntity order = requireOrder(orderId);
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.REJECT, from);

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setCompleteRemark(reason);
        update.setVersion(order.getVersion());
        workOrderMapper.updateById(update);

        TransitionResult transition = transitionRequest(order.getRequestId(),
                RequestTransitions.Action.REJECT, RequestTransitions.Status.REJECTED, reason, null);

        requestLogRecorder.record(order.getRequestId(), order.getId(), RequestTransitions.Action.REJECT,
                transition.from(), transition.to(), operator.userId(), operator.displayName(), reason);

        if (transition.changed() && transition.request() != null) {
            notificationService.send(new NotificationSendCommand(transition.request().getRequesterId(), "REQUEST",
                    "申请未受理", "你的申请「" + order.getTitle() + "」未能受理：" + reason,
                    "SERVICE_REQUEST", order.getRequestId()));
        }
        log.info("工单已驳回, orderId={}, operatorId={}", orderId, operator.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(AuthenticatedUser worker, Long orderId) {
        WorkOrderEntity order = requireOrder(orderId);
        requireAssignee(order, worker.userId());
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.START, from);

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setStartedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        workOrderMapper.updateById(update);

        requestLogRecorder.record(order.getRequestId(), order.getId(), RequestTransitions.Action.START,
                RequestTransitions.Status.PROCESSING, RequestTransitions.Status.PROCESSING,
                worker.userId(), worker.displayName(), "开始处理");

        UserEntity requester = findRequester(order.getRequestId());
        if (requester != null) {
            notificationService.send(new NotificationSendCommand(requester.getId(), "REQUEST",
                    "申请处理中", "服务人员已开始处理你的申请「" + order.getTitle() + "」。",
                    "SERVICE_REQUEST", order.getRequestId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(AuthenticatedUser worker, Long orderId, String remark) {
        WorkOrderEntity order = requireOrder(orderId);
        requireAssignee(order, worker.userId());
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.COMPLETE, from);

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setCompleteRemark(remark);
        update.setCompletedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        workOrderMapper.updateById(update);

        TransitionResult transition = transitionRequest(order.getRequestId(),
                RequestTransitions.Action.COMPLETE, RequestTransitions.Status.COMPLETED, null, LocalDateTime.now());

        requestLogRecorder.record(order.getRequestId(), order.getId(), RequestTransitions.Action.COMPLETE,
                transition.from(), transition.to(), worker.userId(), worker.displayName(), remark);

        if (transition.changed() && transition.request() != null) {
            notificationService.send(new NotificationSendCommand(transition.request().getRequesterId(), "REQUEST",
                    "申请已完成", "你的申请「" + order.getTitle() + "」已办结，快来评价本次服务吧。",
                    "SERVICE_REQUEST", order.getRequestId()));
        }
        log.info("工单已办结, orderId={}, workerId={}", orderId, worker.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelForRequest(ServiceRequestEntity request, AuthenticatedUser operator, String reason) {
        WorkOrderEntity order = workOrderMapper.findByRequestId(request.getId());
        if (order == null) {
            return;
        }
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.CANCEL, from);

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setCompleteRemark("申请人撤销：" + reason);
        update.setCompletedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        workOrderMapper.updateById(update);

        requestLogRecorder.record(request.getId(), order.getId(), RequestTransitions.Action.CANCEL,
                RequestTransitions.Status.PROCESSING, RequestTransitions.Status.CANCELLED,
                operator.userId(), operator.displayName(), reason);

        if (order.getAssigneeId() != null) {
            notificationService.send(new NotificationSendCommand(order.getAssigneeId(), "WORK_ORDER",
                    "工单已撤销", "工单「" + order.getTitle() + "」已被申请人撤销，无需继续处理。",
                    "WORK_ORDER", order.getId()));
        }
    }

    @Override
    public PageResult<WorkOrderSummaryResponse> pageQueue(AuthenticatedUser dispatcher, WorkOrderPageQuery query) {
        WorkOrderTransitions.Status status = parseOptionalStatus(query.status());
        List<Long> visibleOrgIds = dispatcherScopeResolver.resolveVisibleOrganizationIds(dispatcher);
        if (visibleOrgIds != null && visibleOrgIds.isEmpty()) {
            return PageResult.of(query.pageNumOrDefault(), query.pageSizeOrDefault(), 0, List.of());
        }
        LambdaQueryWrapper<WorkOrderEntity> wrapper = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getCampusId, dispatcher.campusId())
                .eq(status != null, WorkOrderEntity::getStatus, status == null ? null : status.name())
                .in(visibleOrgIds != null, WorkOrderEntity::getAssignedOrganizationId, visibleOrgIds)
                .orderByDesc(WorkOrderEntity::getCreatedAt);
        Page<WorkOrderEntity> page = workOrderMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        return PageResult.of(page.convert(WorkOrderSummaryResponse::from));
    }

    @Override
    public PageResult<WorkOrderSummaryResponse> pageAssigned(AuthenticatedUser worker, WorkOrderPageQuery query) {
        WorkOrderTransitions.Status status = parseOptionalStatus(query.status());
        LambdaQueryWrapper<WorkOrderEntity> wrapper = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getAssigneeId, worker.userId())
                .eq(status != null, WorkOrderEntity::getStatus, status == null ? null : status.name())
                .orderByDesc(WorkOrderEntity::getUpdatedAt);
        Page<WorkOrderEntity> page = workOrderMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        return PageResult.of(page.convert(WorkOrderSummaryResponse::from));
    }

    @Override
    public WorkOrderDetailResponse detailForDispatcher(AuthenticatedUser dispatcher, Long orderId) {
        WorkOrderEntity order = requireOrder(orderId);
        List<Long> visibleOrgIds = dispatcherScopeResolver.resolveVisibleOrganizationIds(dispatcher);
        boolean visible = visibleOrgIds == null
                || (order.getAssignedOrganizationId() != null && visibleOrgIds.contains(order.getAssignedOrganizationId()));
        if (!visible) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return toDetail(order);
    }

    @Override
    public WorkOrderDetailResponse detailForWorker(AuthenticatedUser worker, Long orderId) {
        WorkOrderEntity order = requireOrder(orderId);
        if (!worker.userId().equals(order.getAssigneeId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ASSIGNED_TO_CURRENT_USER);
        }
        return toDetail(order);
    }

    private WorkOrderDetailResponse toDetail(WorkOrderEntity order) {
        ServiceRequestEntity request = serviceRequestMapper.selectById(order.getRequestId());
        String requestNo = request == null ? null : request.getRequestNo();
        String requesterName = null;
        JsonNode formDataNode = null;
        if (request != null) {
            UserEntity requester = userMapper.selectById(request.getRequesterId());
            requesterName = requester == null ? null : requester.getDisplayName();
            JsonNode formData = parseFormData(request.getFormData());
            formData = enrichImageUrls(order, formData);
            formDataNode = formData;
        }
        return new WorkOrderDetailResponse(order.getId(), order.getOrderNo(), order.getRequestId(), requestNo,
                order.getTitle(), order.getStatus(), order.getPriority(), order.getAssigneeName(),
                requesterName, formDataNode, order.getCreatedAt(), order.getAssignedAt(), order.getStartedAt(),
                order.getCompletedAt(), order.getCompleteRemark());
    }

    /** 图片字段值替换为短时签名访问路径 */
    private JsonNode enrichImageUrls(WorkOrderEntity order, JsonNode formData) {
        if (formData == null) {
            return null;
        }
        try {
            var item = serviceItemMapper.selectById(order.getItemId());
            if (item == null || item.getFormSchema() == null) {
                return formData;
            }
            return fileStorageService.replaceImageValuesWithSignedUrls(
                    formData, objectMapper.readTree(item.getFormSchema()));
        } catch (Exception exception) {
            log.warn("工单表单图片链接签名化失败, orderId={}", order.getId());
            return formData;
        }
    }

    private JsonNode parseFormData(String formData) {
        if (formData == null || formData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(formData);
        } catch (Exception exception) {
            log.warn("申请表单数据解析失败, formData 长度={}", formData.length());
            return null;
        }
    }

    private WorkOrderEntity requireOrder(Long orderId) {
        WorkOrderEntity order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void requireAssignee(WorkOrderEntity order, Long userId) {
        if (!userId.equals(order.getAssigneeId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ASSIGNED_TO_CURRENT_USER);
        }
    }

    private boolean hasWorkerRole(Long userId) {
        return identityAccessMapper.findRoleScopes(userId).stream()
                .anyMatch(scope -> ROLE_WORKER.equals(scope.getRoleCode()));
    }

    private UserEntity findRequester(Long requestId) {
        ServiceRequestEntity request = serviceRequestMapper.selectById(requestId);
        if (request == null) {
            return null;
        }
        return userMapper.selectById(request.getRequesterId());
    }

    /**
     * 校验并推进关联申请状态；changed=false 表示状态未变化（如重复派单）。
     */
    private TransitionResult transitionRequest(Long requestId, RequestTransitions.Action action,
                                               RequestTransitions.Status target, String reason,
                                               LocalDateTime completedAt) {
        ServiceRequestEntity request = serviceRequestMapper.selectById(requestId);
        if (request == null) {
            return new TransitionResult(null, null, target, false);
        }
        RequestTransitions.Status from = RequestTransitions.parse(request.getStatus());
        if (from == target) {
            // 重复派单等幂等场景：状态未变化，仅记录时间线
            return new TransitionResult(request, from, target, false);
        }
        RequestTransitions.Status to = RequestTransitions.target(action, from);
        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(request.getId());
        update.setStatus(to.name());
        update.setRejectReason(reason);
        update.setCompletedAt(completedAt);
        update.setVersion(request.getVersion());
        serviceRequestMapper.updateById(update);
        return new TransitionResult(request, from, to, true);
    }

    private TransitionResult transitionRequest(Long requestId, RequestTransitions.Action action,
                                               RequestTransitions.Status target) {
        return transitionRequest(requestId, action, target, null, null);
    }

    private record TransitionResult(ServiceRequestEntity request, RequestTransitions.Status from,
                                    RequestTransitions.Status to, boolean changed) {
    }

    private WorkOrderTransitions.Status parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return WorkOrderTransitions.parse(status);
    }
}
