package com.studenthub.modules.workorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.util.JsonIds;
import com.studenthub.common.util.OrderNoGenerator;
import com.studenthub.exception.BusinessException;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.IdentityAccessMapper;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.WorkerCandidateRow;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicerequest.controller.dto.RequestLogResponse;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.service.RequestBusinessView;
import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.servicerequest.service.ServiceRequestTransitionService;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
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
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.workorder.service.WorkOrderService;
import com.studenthub.modules.workorder.service.WorkOrderSla;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String PRIORITY_URGENT = "URGENT";
    /** 学校报修字典里的紧急类型：停电/停水/漏水，命中即按紧急工单派发 */
    private static final java.util.Set<String> EMERGENCY_FAULT_TYPES = java.util.Set.of("停电", "停水", "漏水");
    private static final String ROLE_WORKER = "WORKER";
    private static final String ORDER_NO_PREFIX = "WO";

    private final WorkOrderMapper workOrderMapper;
    private final ServiceRequestMapper serviceRequestMapper;
    private final UserMapper userMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper serviceItemMapper;
    private final com.studenthub.modules.file.service.FileStorageService fileStorageService;
    private final RequestLogRecorder requestLogRecorder;
    private final ServiceRequestTransitionService requestTransitionService;
    private final NotificationService notificationService;
    private final DispatcherScopeResolver dispatcherScopeResolver;
    private final ObjectMapper objectMapper;
    private final FileStorageMapper fileStorageMapper;
    private final AdminCampusContextService campusContextService;
    private final OrganizationMapper organizationMapper;
    private final ServiceRequestLogMapper requestLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderEntity createPendingForRequest(ServiceRequestEntity request) {
        if (!RequestBusinessView.repair(request))
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "此申请不适用维修派工");
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(OrderNoGenerator.next(ORDER_NO_PREFIX, null));
        order.setRequestId(request.getId());
        order.setCampusId(request.getCampusId());
        order.setItemId(request.getItemId());
        order.setTitle(request.getTitle());
        order.setFlowArchived(false);
        order.setAssignedOrganizationId(request.getHandlerOrganizationId());
        order.setStatus(WorkOrderTransitions.Status.PENDING_DISPATCH.name());
        ServiceItemEntity serviceItem = serviceItemMapper.selectById(request.getItemId());
        int slaHours = serviceItem == null || serviceItem.getSlaHours() == null || serviceItem.getSlaHours() <= 0
                ? WorkOrderSla.DEFAULT_SLA_HOURS : serviceItem.getSlaHours();
        order.setPriority(resolvePriority(request, serviceItem));
        order.setSlaHours(slaHours);
        order.setDueAt(LocalDateTime.now().plusHours(slaHours));
        order.setPhotoWorkflowRequired(serviceItem != null && Boolean.TRUE.equals(serviceItem.getPhotoWorkflowRequired()));
        workOrderMapper.insert(order);

        WorkOrderEntity orderNoUpdate = new WorkOrderEntity();
        orderNoUpdate.setId(order.getId());
        orderNoUpdate.setOrderNo(OrderNoGenerator.next(ORDER_NO_PREFIX, order.getId()));
        orderNoUpdate.setVersion(order.getVersion());
        workOrderMapper.updateById(orderNoUpdate);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(AuthenticatedUser operator, Long orderId, AssignWorkOrderRequest request) {
        WorkOrderEntity order = requireOrder(orderId);
        requireDispatcherVisible(operator, order);
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.ASSIGN, from);

        UserEntity assignee = requireEnabledWorker(request.assigneeId());

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setAssigneeId(assignee.getId());
        update.setAssigneeName(assignee.getDisplayName());
        update.setStatus(to.name());
        update.setAssignedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");

        TransitionResult transition = toResult(requestTransitionService.apply(order.getRequestId(),
                RequestTransitions.Action.DISPATCH, null, null));

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
    public void reassign(AuthenticatedUser operator, Long orderId, ReassignWorkOrderRequest request) {
        WorkOrderEntity order = requireOrder(orderId);
        requireDispatcherVisible(operator, order);
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        if (from != WorkOrderTransitions.Status.ASSIGNED && from != WorkOrderTransitions.Status.PROCESSING
                && from != WorkOrderTransitions.Status.ONSITE_PHOTO_RECORDED) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "仅已派发或处理中的工单可改派");
        }
        if (order.getAssigneeId() != null && order.getAssigneeId().equals(request.assigneeId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "新处理人与当前处理人相同");
        }
        UserEntity assignee = requireEnabledWorker(request.assigneeId());
        Long previousAssigneeId = order.getAssigneeId();
        String previousAssigneeName = order.getAssigneeName();

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setAssigneeId(assignee.getId());
        update.setAssigneeName(assignee.getDisplayName());
        update.setAssignedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");

        ServiceRequestEntity serviceRequest = serviceRequestMapper.selectById(order.getRequestId());
        RequestTransitions.Status requestStatus = serviceRequest == null
                ? RequestTransitions.Status.PROCESSING
                : RequestTransitions.parse(serviceRequest.getStatus());
        String comment = request.comment() == null || request.comment().isBlank()
                ? "改派工单" : request.comment().trim();
        requestLogRecorder.record(order.getRequestId(), order.getId(), RequestTransitions.Action.DISPATCH,
                requestStatus, requestStatus, operator.userId(), operator.displayName(),
                "改派：" + (previousAssigneeName == null ? "未指派" : previousAssigneeName)
                        + " → " + assignee.getDisplayName() + "；" + comment);

        notificationService.send(new NotificationSendCommand(assignee.getId(), "WORK_ORDER",
                "新工单待处理", "工单「" + order.getTitle() + "」已改派给你，请及时处理。",
                "WORK_ORDER", order.getId()));
        if (previousAssigneeId != null) {
            notificationService.send(new NotificationSendCommand(previousAssigneeId, "WORK_ORDER",
                    "工单已改派", "工单「" + order.getTitle() + "」已改派给 " + assignee.getDisplayName()
                            + "，无需继续处理。", "WORK_ORDER", order.getId()));
        }
        log.info("工单已改派, orderId={}, fromAssigneeId={}, toAssigneeId={}, operatorId={}",
                orderId, previousAssigneeId, assignee.getId(), operator.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchAssignWorkOrderResponse batchAssign(AuthenticatedUser operator,
                                                    BatchAssignWorkOrderRequest request) {
        UserEntity assignee = requireEnabledWorker(request.assigneeId());
        List<Long> orderIds = request.orderIds().stream().filter(Objects::nonNull).distinct().toList();
        List<BatchAssignWorkOrderResponse.Failure> failures = new ArrayList<>();
        List<Long> assignable = new ArrayList<>();
        for (Long orderId : orderIds) {
            try {
                WorkOrderEntity order = requireOrder(orderId);
                requireDispatcherVisible(operator, order);
                WorkOrderTransitions.Status status = WorkOrderTransitions.parse(order.getStatus());
                if (status != WorkOrderTransitions.Status.PENDING_DISPATCH) {
                    failures.add(new BatchAssignWorkOrderResponse.Failure(orderId,
                            "仅待派发工单可批量派发，当前状态：" + status.getDisplay()));
                    continue;
                }
                assignable.add(orderId);
            } catch (BusinessException exception) {
                failures.add(new BatchAssignWorkOrderResponse.Failure(orderId, exception.getMessage()));
            }
        }
        int success = 0;
        for (Long orderId : assignable) {
            try {
                assign(operator, orderId, new AssignWorkOrderRequest(assignee.getId(), request.comment()));
                success++;
            } catch (BusinessException exception) {
                failures.add(new BatchAssignWorkOrderResponse.Failure(orderId, exception.getMessage()));
            }
        }
        log.info("工单批量派发完成, requested={}, success={}, failed={}, assigneeId={}",
                orderIds.size(), success, failures.size(), assignee.getId());
        return new BatchAssignWorkOrderResponse(orderIds.size(), success, failures);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(AuthenticatedUser operator, Long orderId, String reason) {
        WorkOrderEntity order = requireOrder(orderId);
        requireDispatcherVisible(operator, order);
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.REJECT, from);

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setCompleteRemark(reason);
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");

        TransitionResult transition = toResult(requestTransitionService.apply(order.getRequestId(),
                RequestTransitions.Action.REJECT, reason, null));

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
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");

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
        if (Boolean.TRUE.equals(order.getPhotoWorkflowRequired())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "报修工单必须完成现场拍照和完工拍照后提交确认");
        }
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.COMPLETE, from);

        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setCompleteRemark(remark);
        update.setCompletedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");

        TransitionResult transition = toResult(requestTransitionService.apply(order.getRequestId(),
                RequestTransitions.Action.COMPLETE, null, LocalDateTime.now()));

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
    public void recordArrivalPhoto(AuthenticatedUser worker, Long orderId, Long fileId) {
        WorkOrderEntity order = requireOrder(orderId);
        requireAssignee(order, worker.userId());
        requirePhotoWorkflow(order);
        validateOwnedImage(fileId, worker.userId());
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(
                WorkOrderTransitions.Action.RECORD_ARRIVAL_PHOTO, from);
        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setArrivalPhotoFileId(fileId);
        update.setStatus(to.name());
        update.setConfirmationRejectReason(null);
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");
        recordPhotoLog(order, from, to, worker, RequestTransitions.Action.ARRIVAL_PHOTO, "现场拍照打卡");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitCompletion(AuthenticatedUser worker, Long orderId, SubmitWorkOrderCompletionRequest request) {
        WorkOrderEntity order = requireOrder(orderId);
        requireAssignee(order, worker.userId());
        requirePhotoWorkflow(order);
        validateOwnedImage(request.fileId(), worker.userId());
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(
                WorkOrderTransitions.Action.SUBMIT_COMPLETION, from);
        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setCompletionPhotoFileId(request.fileId());
        List<Long> completionPhotoIds = JsonIds.parse(order.getCompletionPhotoFileIds());
        if (!completionPhotoIds.contains(request.fileId())) completionPhotoIds.add(request.fileId());
        update.setCompletionPhotoFileIds(JsonIds.write(completionPhotoIds));
        update.setCompleteRemark(request.remark().trim());
        update.setCompletionSubmittedAt(LocalDateTime.now());
        update.setStatus(to.name());
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");
        recordPhotoLog(order, from, to, worker, RequestTransitions.Action.COMPLETION_PHOTO, request.remark().trim());
        ServiceRequestEntity serviceRequest = serviceRequestMapper.selectById(order.getRequestId());
        if (serviceRequest != null) {
            notificationService.send(new NotificationSendCommand(serviceRequest.getRequesterId(), "REQUEST",
                    "工单待确认", "工单「" + order.getTitle() + "」已完成，请确认处理结果。",
                    "SERVICE_REQUEST", serviceRequest.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmCompletion(AuthenticatedUser requester, Long orderId) {
        WorkOrderEntity order = requireOrder(orderId);
        requireRequester(order, requester.userId());
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(WorkOrderTransitions.Action.CONFIRM, from);
        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setConfirmedAt(LocalDateTime.now());
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");
        ServiceRequestEntity serviceRequest = serviceRequestMapper.selectById(order.getRequestId());
        if (serviceRequest != null) {
            requestTransitionService.apply(serviceRequest.getId(), RequestTransitions.Action.COMPLETE,
                    null, LocalDateTime.now());
            requestLogRecorder.record(serviceRequest.getId(), order.getId(), RequestTransitions.Action.COMPLETE,
                    RequestTransitions.Status.PROCESSING, RequestTransitions.Status.COMPLETED,
                    requester.userId(), requester.displayName(), "报修人确认完成");
        }
        if (order.getAssigneeId() != null) {
            notificationService.send(new NotificationSendCommand(order.getAssigneeId(), "WORK_ORDER",
                    "报修人已确认", "工单「" + order.getTitle() + "」已确认办结。", "WORK_ORDER", order.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectCompletion(AuthenticatedUser requester, Long orderId, String reason) {
        WorkOrderEntity order = requireOrder(orderId);
        requireRequester(order, requester.userId());
        WorkOrderTransitions.Status from = WorkOrderTransitions.parse(order.getStatus());
        WorkOrderTransitions.Status to = WorkOrderTransitions.target(
                WorkOrderTransitions.Action.REOPEN_CONFIRMATION, from);
        WorkOrderEntity update = new WorkOrderEntity();
        update.setId(order.getId());
        update.setStatus(to.name());
        update.setCompletionSubmittedAt(null);
        update.setConfirmationRejectReason(reason.trim());
        update.setVersion(order.getVersion());
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");
        requestLogRecorder.record(order.getRequestId(), order.getId(), RequestTransitions.Action.REOPEN,
                RequestTransitions.Status.PROCESSING, RequestTransitions.Status.PROCESSING,
                requester.userId(), requester.displayName(), "报修人退回：" + reason.trim());
        if (order.getAssigneeId() != null) {
            notificationService.send(new NotificationSendCommand(order.getAssigneeId(), "WORK_ORDER",
                    "工单需重新拍照", "工单「" + order.getTitle() + "」被退回，请重新上传完工照片。",
                    "WORK_ORDER", order.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelForRequest(ServiceRequestEntity request, AuthenticatedUser operator, String reason) {
        WorkOrderEntity order = workOrderMapper.findByRequestId(request.getId());
        if (order == null || Boolean.TRUE.equals(order.getFlowArchived()) || !RequestBusinessView.repair(request)) {
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
        if (workOrderMapper.updateById(update) == 0)
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单状态已变更，请刷新");

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
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WorkOrderEntity> wrapper = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getFlowArchived, false)
                .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getCampusId, campusContextService.currentCampusId())
                .eq(status != null, WorkOrderEntity::getStatus, status == null ? null : status.name())
                .in(visibleOrgIds != null, WorkOrderEntity::getAssignedOrganizationId, visibleOrgIds);
        if (Boolean.TRUE.equals(query.overdue())) {
            wrapper.lt(WorkOrderEntity::getDueAt, now)
                    .in(WorkOrderEntity::getStatus, WorkOrderTransitions.inProgressSet())
                    .orderByAsc(WorkOrderEntity::getDueAt);
        } else if (Boolean.TRUE.equals(query.dueSoon())) {
            wrapper.ge(WorkOrderEntity::getDueAt, now)
                    .le(WorkOrderEntity::getDueAt, now.plusHours(WorkOrderSla.DUE_SOON_HOURS))
                    .in(WorkOrderEntity::getStatus, WorkOrderTransitions.inProgressSet())
                    .orderByAsc(WorkOrderEntity::getDueAt);
        } else {
            wrapper.orderByDesc(WorkOrderEntity::getCreatedAt);
        }
        Page<WorkOrderEntity> page = workOrderMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        return PageResult.of(page.convert(WorkOrderSummaryResponse::from));
    }

    @Override
    public PageResult<WorkOrderSummaryResponse> pageAssigned(AuthenticatedUser worker, WorkOrderPageQuery query) {
        WorkOrderTransitions.Status status = parseOptionalStatus(query.status());
        boolean processingGroup = status == WorkOrderTransitions.Status.PROCESSING;
        LambdaQueryWrapper<WorkOrderEntity> wrapper = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getFlowArchived, false)
                .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getAssigneeId, worker.userId())
                .eq(status != null && !processingGroup, WorkOrderEntity::getStatus, status == null ? null : status.name())
                .in(processingGroup, WorkOrderEntity::getStatus,
                        WorkOrderTransitions.Status.PROCESSING.name(),
                        WorkOrderTransitions.Status.ONSITE_PHOTO_RECORDED.name(),
                        WorkOrderTransitions.Status.PENDING_CONFIRMATION.name())
                .orderByDesc(WorkOrderEntity::getUpdatedAt);
        Page<WorkOrderEntity> page = workOrderMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        return PageResult.of(page.convert(WorkOrderSummaryResponse::from));
    }

    @Override
    public List<WorkloadItemResponse> workload(AuthenticatedUser dispatcher) {
        Long campusId = campusContextService.currentCampusId();
        List<Long> visibleOrgIds = dispatcherScopeResolver.resolveVisibleOrganizationIds(dispatcher);
        if (visibleOrgIds != null && visibleOrgIds.isEmpty()) {
            return List.of();
        }
        List<WorkerCandidateRow> workers = userMapper.findEnabledWorkers(campusId);
        if (workers.isEmpty()) {
            return List.of();
        }
        List<Long> workerIds = workers.stream().map(WorkerCandidateRow::getUserId).toList();
        Map<Long, UserEntity> userMap = new LinkedHashMap<>();
        for (UserEntity user : userMapper.selectBatchIds(workerIds)) {
            userMap.put(user.getId(), user);
        }
        if (visibleOrgIds != null) {
            workers = workers.stream().filter(worker -> {
                UserEntity user = userMap.get(worker.getUserId());
                return user != null && user.getDefaultOrganizationId() != null
                        && visibleOrgIds.contains(user.getDefaultOrganizationId());
            }).toList();
            if (workers.isEmpty()) {
                return List.of();
            }
        }
        Map<Long, String> organizationNames = organizationNameMap(workers.stream()
                .map(worker -> userMap.get(worker.getUserId()))
                .filter(Objects::nonNull)
                .map(UserEntity::getDefaultOrganizationId)
                .filter(Objects::nonNull)
                .toList());
        List<Long> activeWorkerIds = workers.stream().map(WorkerCandidateRow::getUserId).toList();
        List<WorkOrderEntity> activeOrders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getFlowArchived, false)
                .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getCampusId, campusId)
                .in(WorkOrderEntity::getAssigneeId, activeWorkerIds)
                .in(WorkOrderEntity::getStatus, WorkOrderTransitions.inProgressSet()));
        LocalDateTime now = LocalDateTime.now();
        Map<Long, LoadCounter> counters = new LinkedHashMap<>();
        for (WorkOrderEntity order : activeOrders) {
            LoadCounter counter = counters.computeIfAbsent(order.getAssigneeId(), key -> new LoadCounter());
            counter.active++;
            if (WorkOrderSla.overdue(order, now)) {
                counter.overdue++;
            } else if (WorkOrderSla.dueSoon(order, now)) {
                counter.dueSoon++;
            }
            if (WorkOrderTransitions.Status.PENDING_CONFIRMATION.name().equals(order.getStatus())) {
                counter.pendingConfirmation++;
            }
        }
        return workers.stream()
                .map(worker -> {
                    UserEntity user = userMap.get(worker.getUserId());
                    LoadCounter counter = counters.getOrDefault(worker.getUserId(), new LoadCounter());
                    Long organizationId = user == null ? null : user.getDefaultOrganizationId();
                    return new WorkloadItemResponse(worker.getUserId(), worker.getUsername(),
                            worker.getDisplayName(), PhoneMasker.mask(worker.getPhone()), organizationId,
                            organizationId == null ? null : organizationNames.get(organizationId),
                            counter.active, counter.overdue, counter.dueSoon, counter.pendingConfirmation);
                })
                .sorted(Comparator.comparingLong(WorkloadItemResponse::activeCount)
                        .thenComparing(WorkloadItemResponse::displayName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public WorkOrderSlaStatsResponse slaStats(AuthenticatedUser dispatcher) {
        Long campusId = campusContextService.currentCampusId();
        List<Long> visibleOrgIds = dispatcherScopeResolver.resolveVisibleOrganizationIds(dispatcher);
        if (visibleOrgIds != null && visibleOrgIds.isEmpty()) {
            return emptyStats();
        }
        List<WorkOrderEntity> inProgressOrders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getFlowArchived, false)
                .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getCampusId, campusId)
                .in(WorkOrderEntity::getStatus, WorkOrderTransitions.inProgressSet())
                .in(visibleOrgIds != null, WorkOrderEntity::getAssignedOrganizationId, visibleOrgIds));
        LocalDateTime now = LocalDateTime.now();
        long pendingDispatch = 0;
        long assigned = 0;
        long processing = 0;
        long onsitePhotoRecorded = 0;
        long pendingConfirmation = 0;
        long overdue = 0;
        long dueSoon = 0;
        for (WorkOrderEntity order : inProgressOrders) {
            switch (WorkOrderTransitions.Status.valueOf(order.getStatus())) {
                case PENDING_DISPATCH -> pendingDispatch++;
                case ASSIGNED -> assigned++;
                case PROCESSING -> processing++;
                case ONSITE_PHOTO_RECORDED -> onsitePhotoRecorded++;
                case PENDING_CONFIRMATION -> pendingConfirmation++;
                default -> {
                    // 终态不会出现在进行中集合
                }
            }
            if (WorkOrderSla.overdue(order, now)) {
                overdue++;
            } else if (WorkOrderSla.dueSoon(order, now)) {
                dueSoon++;
            }
        }
        LocalDateTime since = now.minusDays(30);
        List<WorkOrderEntity> completedOrders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getFlowArchived, false)
                .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getCampusId, campusId)
                .eq(WorkOrderEntity::getStatus, WorkOrderTransitions.Status.COMPLETED.name())
                .ge(WorkOrderEntity::getCompletedAt, since)
                .in(visibleOrgIds != null, WorkOrderEntity::getAssignedOrganizationId, visibleOrgIds));
        long processMinutes = 0;
        long processCount = 0;
        long completionMinutes = 0;
        long completionCount = 0;
        for (WorkOrderEntity order : completedOrders) {
            LocalDateTime completedAt = order.getCompletedAt();
            if (completedAt == null) {
                continue;
            }
            LocalDateTime startedAt = order.getStartedAt() != null ? order.getStartedAt() : order.getAssignedAt();
            if (startedAt != null) {
                processMinutes += Duration.between(startedAt, completedAt).toMinutes();
                processCount++;
            }
            if (order.getCreatedAt() != null) {
                completionMinutes += Duration.between(order.getCreatedAt(), completedAt).toMinutes();
                completionCount++;
            }
        }
        long inProgressTotal = inProgressOrders.size();
        Double overdueRate = inProgressTotal == 0 ? null
                : WorkOrderSla.round1(overdue * 100.0 / inProgressTotal);
        return new WorkOrderSlaStatsResponse(pendingDispatch, assigned, processing, onsitePhotoRecorded,
                pendingConfirmation, inProgressTotal, overdue, dueSoon, completedOrders.size(),
                processCount == 0 ? null : WorkOrderSla.round1(processMinutes / 60.0 / processCount),
                completionCount == 0 ? null : WorkOrderSla.round1(completionMinutes / 60.0 / completionCount),
                overdueRate);
    }

    private WorkOrderSlaStatsResponse emptyStats() {
        return new WorkOrderSlaStatsResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null);
    }

    private Map<Long, String> organizationNameMap(List<Long> organizationIds) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (organizationIds == null || organizationIds.isEmpty()) {
            return names;
        }
        for (OrganizationEntity organization : organizationMapper.selectBatchIds(organizationIds.stream().distinct().toList())) {
            names.put(organization.getId(), organization.getOrganizationName());
        }
        return names;
    }

    private void requireDispatcherVisible(AuthenticatedUser dispatcher, WorkOrderEntity order) {
        List<Long> visibleOrgIds = dispatcherScopeResolver.resolveVisibleOrganizationIds(dispatcher);
        boolean visible = visibleOrgIds == null
                || (order.getAssignedOrganizationId() != null
                && visibleOrgIds.contains(order.getAssignedOrganizationId()));
        if (!visible) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    private UserEntity requireEnabledWorker(Long assigneeId) {
        UserEntity assignee = userMapper.selectById(assigneeId);
        if (assignee == null || !"ENABLED".equals(assignee.getStatus()) || !hasWorkerRole(assigneeId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "被指派人不是在岗服务人员");
        }
        return assignee;
    }

    private static final class LoadCounter {
        private long active;
        private long overdue;
        private long dueSoon;
        private long pendingConfirmation;
    }

    @Override
    public WorkOrderDetailResponse detailForDispatcher(AuthenticatedUser dispatcher, Long orderId) {
        WorkOrderEntity order = requireOrder(orderId);
        requireDispatcherVisible(dispatcher, order);
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
        String requesterPhone = null;
        JsonNode formDataNode = null;
        if (request != null) {
            UserEntity requester = userMapper.selectById(request.getRequesterId());
            requesterName = requester == null ? null : requester.getDisplayName();
            requesterPhone = requester == null ? null : requester.getPhone();
            JsonNode formData = parseFormData(request.getFormData());
            formData = enrichImageUrls(order, formData);
            formDataNode = formData;
        }
        ServiceItemEntity item = serviceItemMapper.selectById(order.getItemId());
        JsonNode schemaNode = parseSchema(item == null ? null : item.getFormSchema());
        String repairAddress = formDataNode == null ? null : repairAddressOf(formDataNode);
        String arrivalPhotoUrl = signPhoto(order.getArrivalPhotoFileId());
        String completionPhotoUrl = signPhoto(order.getCompletionPhotoFileId());
        List<String> completionPhotoUrls = JsonIds.parse(order.getCompletionPhotoFileIds()).stream()
                .map(this::signPhoto).filter(java.util.Objects::nonNull).toList();
        if (completionPhotoUrls.isEmpty() && completionPhotoUrl != null) completionPhotoUrls = List.of(completionPhotoUrl);
        // 师傅电话：处理人联系方式，供报修人一键拨打（需求清单 二-2）
        UserEntity assignee = order.getAssigneeId() == null ? null : userMapper.selectById(order.getAssigneeId());
        String assigneePhone = assignee == null ? null : assignee.getPhone();
        List<RequestLogResponse> timeline = requestLogMapper.selectList(
                        new LambdaQueryWrapper<ServiceRequestLogEntity>()
                                .eq(ServiceRequestLogEntity::getRequestId, order.getRequestId())
                                .orderByAsc(ServiceRequestLogEntity::getCreatedAt)
                                .orderByAsc(ServiceRequestLogEntity::getId))
                .stream().map(RequestLogResponse::from).toList();
        return WorkOrderDetailResponse.from(order, requestNo, requesterName, requesterPhone, assigneePhone,
                formDataNode, schemaNode, repairAddress,
                arrivalPhotoUrl, completionPhotoUrl, completionPhotoUrls, timeline);
    }

    /**
     * 紧急类型按紧急工单派发：表单里 faultType 命中停电/停水/漏水即提优先级，
     * 其余保持 NORMAL（学校报修字典的 sfjj 标记与此一致）。
     */
    private String resolvePriority(ServiceRequestEntity request, ServiceItemEntity serviceItem) {
        JsonNode formData = parseFormData(request.getFormData());
        if (formData != null) {
            JsonNode faultType = formData.get("faultType");
            if (faultType != null && !faultType.isNull()
                    && EMERGENCY_FAULT_TYPES.contains(faultType.asText())) {
                return PRIORITY_URGENT;
            }
        }
        // 服务目录默认优先级：未命中紧急类型时，运营可把高危事项预置为紧急工单
        if (serviceItem != null && PRIORITY_URGENT.equals(serviceItem.getDefaultPriority())) {
            return PRIORITY_URGENT;
        }
        return PRIORITY_NORMAL;
    }

    /** 宿舍报修地址 = 宿舍楼 + 房间号；公共区域报修没有楼栋字段，回退为 location */
    private String repairAddressOf(JsonNode formData) {
        String building = textValue(formData, "dormBuilding");
        String room = textValue(formData, "address", "location");
        if (building == null) {
            return room;
        }
        return room == null ? building : building + " " + room;
    }

    private JsonNode parseSchema(String schema) {
        if (schema == null || schema.isBlank()) return objectMapper.createArrayNode();
        try { return objectMapper.readTree(schema); }
        catch (Exception ignored) { return objectMapper.createArrayNode(); }
    }

    private String textValue(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private String signPhoto(Long fileId) {
        return fileId == null ? null : fileStorageService.signViewPath(fileId);
    }

    private void requirePhotoWorkflow(WorkOrderEntity order) {
        if (!Boolean.TRUE.equals(order.getPhotoWorkflowRequired())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "该工单不需要拍照确认");
        }
    }

    private void validateOwnedImage(Long fileId, Long userId) {
        var file = fileStorageMapper.selectById(fileId);
        if (file == null || !userId.equals(file.getUploaderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能使用自己上传的图片");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工单打卡必须上传图片");
        }
    }

    private void requireRequester(WorkOrderEntity order, Long userId) {
        ServiceRequestEntity request = serviceRequestMapper.selectById(order.getRequestId());
        if (request == null || !userId.equals(request.getRequesterId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有报修人可以确认该工单");
        }
    }

    private void recordPhotoLog(WorkOrderEntity order, WorkOrderTransitions.Status from,
                                WorkOrderTransitions.Status to, AuthenticatedUser worker,
                                RequestTransitions.Action action, String comment) {
        requestLogRecorder.record(order.getRequestId(), order.getId(), action,
                RequestTransitions.Status.PROCESSING, RequestTransitions.Status.PROCESSING,
                worker.userId(), worker.displayName(), comment);
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
        WorkOrderEntity order = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                ? workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrderEntity>()
                    .eq(WorkOrderEntity::getId, orderId).last("FOR UPDATE"))
                : workOrderMapper.selectById(orderId);
        ServiceRequestEntity request = order == null ? null : serviceRequestMapper.selectById(order.getRequestId());
        if (order == null || !Boolean.FALSE.equals(order.getFlowArchived()) || request == null
                || !RequestBusinessView.repair(request)) {
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
    private TransitionResult toResult(ServiceRequestTransitionService.TransitionResult result) {
        return new TransitionResult(result.request(), result.from(), result.to(), result.changed());
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
