package com.studenthub.modules.servicerequest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.common.util.JsonIds;
import com.studenthub.common.util.OrderNoGenerator;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicecatalog.service.ServiceCatalogService;
import com.studenthub.modules.servicecatalog.enums.ServiceFlowType;
import com.studenthub.modules.servicerequest.service.RequestBusinessView;
import com.studenthub.modules.servicerequest.service.RequestRoutingNotifications;
import com.studenthub.modules.servicerequest.controller.dto.RequestLogResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.controller.dto.SubmitServiceRequestRequest;
import com.studenthub.modules.servicerequest.controller.dto.UrgeResponse;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.servicerequest.service.ServiceRequestService;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import com.studenthub.modules.workorder.controller.dto.WorkOrderSummaryResponse;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import com.studenthub.modules.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRequestServiceImpl implements ServiceRequestService {

    private static final String REQUEST_NO_PREFIX = "SR";

    /** 催单冷却期（小时）：单条申请每 2 小时仅允许催单 1 次 */
    private static final long URGE_COOLDOWN_HOURS = 2;

    private final ServiceRequestMapper serviceRequestMapper;
    private final ServiceRequestLogMapper requestLogMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final RequestLogRecorder requestLogRecorder;
    private final ServiceCatalogService serviceCatalogService;
    private final WorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final com.studenthub.modules.file.service.FileStorageService fileStorageService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final RequestRoutingNotifications routingNotifications;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceRequestDetailResponse submit(AuthenticatedUser requester, SubmitServiceRequestRequest request,
                                               String idempotencyKey) {
        ServiceRequestEntity previous = idempotencyKey == null || idempotencyKey.isBlank() ? null
                : findByIdempotencyKey(requester.userId(), idempotencyKey.trim());
        if (previous != null) return detail(requester, previous.getId());
        ServiceItemEntity item = serviceCatalogService.requireEnabledItem(request.itemId());
        ServiceFlowType flow = ServiceFlowType.resolve(item.getFlowType());
        if (flow == ServiceFlowType.GUIDE) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "此事项仅提供办事指南，请按指南办理");
        }
        String normalizedFormData = serviceCatalogService.validateAndNormalizeFormData(
                request.itemId(), request.formData());

        ServiceRequestEntity entity = new ServiceRequestEntity();
        entity.setRequestNo(OrderNoGenerator.next(REQUEST_NO_PREFIX, null));
        entity.setCampusId(item.getCampusId());
        entity.setItemId(item.getId());
        entity.setItemName(item.getItemName());
        entity.setItemCode(item.getItemCode());
        entity.setFlowType(flow.name());
        entity.setCategoryId(item.getCategoryId());
        entity.setRequesterId(requester.userId());
        entity.setTitle(request.title() == null || request.title().isBlank()
                ? item.getItemName() : request.title().trim());
        entity.setFormData(normalizedFormData);
        applyLeaveFields(entity, normalizedFormData);
        entity.setStatus(RequestTransitions.Status.SUBMITTED.name());
        entity.setHandlerOrganizationId(item.getHandlerOrganizationId());
        entity.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank()
                ? null : idempotencyKey.trim());
        entity.setSubmittedAt(LocalDateTime.now());
        try {
            serviceRequestMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            if (entity.getIdempotencyKey() != null) {
                ServiceRequestEntity existing = findByIdempotencyKey(requester.userId(), entity.getIdempotencyKey());
                if (existing != null) {
                    log.info("申请重复提交命中幂等键, requestId={}, requesterId={}", existing.getId(), requester.userId());
                    return detail(requester, existing.getId());
                }
            }
            throw new BusinessException(ErrorCode.REQUEST_DUPLICATE);
        }

        ServiceRequestEntity requestNoUpdate = new ServiceRequestEntity();
        requestNoUpdate.setId(entity.getId());
        requestNoUpdate.setRequestNo(OrderNoGenerator.next(REQUEST_NO_PREFIX, entity.getId()));
        requestNoUpdate.setVersion(entity.getVersion());
        serviceRequestMapper.updateById(requestNoUpdate);
        entity.setRequestNo(requestNoUpdate.getRequestNo());

        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.SUBMIT,
                null, RequestTransitions.Status.SUBMITTED, requester.userId(), requester.displayName(),
                "提交申请");
        if (RequestBusinessView.repair(entity)) workOrderService.createPendingForRequest(entity);

        notificationService.send(new NotificationSendCommand(requester.userId(), "REQUEST",
                "申请已提交", "你的申请「" + entity.getTitle() + "」已提交成功，"
                        + (RequestBusinessView.repair(entity) ? "等待维修调度分配。"
                        : RequestBusinessView.leave(entity) ? "等待请假审批。" : "等待人工受理。"),
                "SERVICE_REQUEST", entity.getId()));
        if (!RequestBusinessView.repair(entity)) routingNotifications.notifyQueue(entity,
                RequestBusinessView.leave(entity) ? "新请假待审批" : "新申请待人工受理",
                "申请「" + entity.getTitle() + "」已提交，请在对应业务队列处理。");
        log.info("服务申请已提交, requestId={}, itemId={}, requesterId={}",
                entity.getId(), item.getId(), requester.userId());
        return detail(requester, entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceRequestDetailResponse cancel(AuthenticatedUser requester, Long requestId, String reason) {
        ServiceRequestEntity entity = requireOwnedRequestForUpdate(requester.userId(), requestId);
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        if (!RequestBusinessView.actions(entity, null, false).contains("CANCEL")) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "当前业务状态不允许撤回");
        }
        RequestTransitions.Status to = RequestTransitions.target(RequestTransitions.Action.CANCEL, from);

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(entity.getId());
        update.setStatus(to.name());
        update.setCancelReason(reason);
        update.setVersion(entity.getVersion());
        if (serviceRequestMapper.updateById(update) == 0) throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "状态已变更，请刷新");
        entity.setStatus(to.name());

        if (RequestBusinessView.repair(entity)) workOrderService.cancelForRequest(entity, requester, reason);
        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.CANCEL,
                from, to, requester.userId(), requester.displayName(), reason);
        notificationService.send(new NotificationSendCommand(requester.userId(), "REQUEST", "申请已撤回",
                "申请「" + entity.getTitle() + "」已撤回。", "SERVICE_REQUEST", entity.getId()));
        if (!RequestBusinessView.repair(entity)) routingNotifications.notifyQueue(entity, "申请已撤回",
                "申请「" + entity.getTitle() + "」已由申请人撤回，无需继续受理。");
        log.info("服务申请已撤销, requestId={}, requesterId={}", requestId, requester.userId());
        return detail(requester, requestId);
    }

    @Override
    public PageResult<ServiceRequestSummaryResponse> pageMy(AuthenticatedUser requester, Integer pageNum,
                                                            Integer pageSize, String status) {
        RequestTransitions.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = RequestTransitions.parse(status);
        }
        LambdaQueryWrapper<ServiceRequestEntity> wrapper = new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getRequesterId, requester.userId())
                .eq(statusEnum != null, ServiceRequestEntity::getStatus, statusEnum == null ? null : statusEnum.name())
                .orderByDesc(ServiceRequestEntity::getCreatedAt);
        Page<ServiceRequestEntity> page = serviceRequestMapper.selectPage(
                new Page<>(pageNumOrDefault(pageNum), pageSizeOrDefault(pageSize)), wrapper);
        List<Long> requestIds = page.getRecords().stream().map(ServiceRequestEntity::getId).toList();
        Map<Long, WorkOrderEntity> orders = requestIds.isEmpty()
                ? Map.of()
                : workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                        .in(WorkOrderEntity::getRequestId, requestIds).eq(WorkOrderEntity::getFlowArchived, false))
                .stream()
                .collect(Collectors.toMap(WorkOrderEntity::getRequestId, Function.identity(), (left, right) -> left));
        java.util.Set<Long> evaluatedIds = requestIds.isEmpty() ? java.util.Set.of()
                : evaluationMapper.selectList(new LambdaQueryWrapper<ServiceEvaluationEntity>()
                        .in(ServiceEvaluationEntity::getRequestId, requestIds))
                        .stream().map(ServiceEvaluationEntity::getRequestId).collect(Collectors.toSet());
        List<ServiceRequestSummaryResponse> records = page.getRecords().stream()
                .map(entity -> ServiceRequestSummaryResponse.from(entity, orders.get(entity.getId()), evaluatedIds.contains(entity.getId())))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public ServiceRequestDetailResponse detail(AuthenticatedUser requester, Long requestId) {
        ServiceRequestEntity entity = requireOwnedRequest(requester.userId(), requestId);
        return toDetail(entity);
    }

    private ServiceRequestDetailResponse toDetail(ServiceRequestEntity entity) {
        ServiceItemEntity item = serviceItemMapper.selectById(entity.getItemId());
        String itemIcon = item == null ? null : item.getIcon();
        Integer slaHours = item == null ? null : item.getSlaHours();

        WorkOrderEntity order = RequestBusinessView.repair(entity) ? workOrderMapper.findByRequestId(entity.getId()) : null;
        if (order != null && Boolean.TRUE.equals(order.getFlowArchived())) order = null;
        List<String> completionPhotoUrls = order == null ? List.of() : JsonIds.parse(order.getCompletionPhotoFileIds()).stream()
                .map(fileStorageService::signViewPath).toList();
        if (order != null && completionPhotoUrls.isEmpty() && order.getCompletionPhotoFileId() != null) {
            completionPhotoUrls = List.of(fileStorageService.signViewPath(order.getCompletionPhotoFileId()));
        }
        WorkOrderSummaryResponse workOrder = order == null ? null : WorkOrderSummaryResponse.from(order,
                assigneePhone(order),
                order.getArrivalPhotoFileId() == null ? null : fileStorageService.signViewPath(order.getArrivalPhotoFileId()),
                order.getCompletionPhotoFileId() == null ? null : fileStorageService.signViewPath(order.getCompletionPhotoFileId()),
                completionPhotoUrls);

        ServiceEvaluationEntity evaluation = evaluationMapper.selectByRequestId(entity.getId());
        EvaluationResponse evaluationResponse = evaluation == null ? null : EvaluationResponse.from(evaluation);

        List<RequestLogResponse> timeline = requestLogMapper.selectList(
                        new LambdaQueryWrapper<ServiceRequestLogEntity>()
                                .eq(ServiceRequestLogEntity::getRequestId, entity.getId())
                                .orderByAsc(ServiceRequestLogEntity::getCreatedAt))
                .stream().map(RequestLogResponse::from).toList();

        JsonNode formData = enrichImageUrls(entity.getId(), parseFormData(entity.getFormData()), item);
        return ServiceRequestDetailResponse.from(entity, itemIcon, slaHours,
                formData, workOrder, evaluationResponse, timeline);
    }

    /** 师傅电话：工单处理人联系方式（需求清单 二-2，供"联系师傅"一键拨打） */
    private String assigneePhone(WorkOrderEntity order) {
        if (order == null || order.getAssigneeId() == null) return null;
        UserEntity assignee = userMapper.selectById(order.getAssigneeId());
        return assignee == null ? null : assignee.getPhone();
    }

    /** 图片字段值替换为短时签名访问路径，供小程序直接展示 */
    private JsonNode enrichImageUrls(Long requestId, JsonNode formData, ServiceItemEntity item) {
        if (formData == null || item == null || item.getFormSchema() == null) {
            return formData;
        }
        try {
            return fileStorageService.replaceImageValuesWithSignedUrls(
                    formData, objectMapper.readTree(item.getFormSchema()));
        } catch (Exception exception) {
            log.warn("申请表单图片链接签名化失败, requestId={}", requestId);
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
            log.warn("申请表单数据解析失败, formData 长度={}", formData == null ? 0 : formData.length());
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UrgeResponse urge(AuthenticatedUser requester, Long requestId) {
        ServiceRequestEntity entity = requireOwnedRequestForUpdate(requester.userId(), requestId);
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        if (!RequestBusinessView.actions(entity, null, false).contains("URGE"))
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "当前业务状态不支持催办");
        RequestTransitions.target(RequestTransitions.Action.URGE, from);

        // 2 小时限频：查最近一条 URGE 日志，未满冷却期直接拒绝催单
        LocalDateTime now = LocalDateTime.now();
        ServiceRequestLogEntity lastUrge = requestLogMapper.selectOne(
                new LambdaQueryWrapper<ServiceRequestLogEntity>()
                        .eq(ServiceRequestLogEntity::getRequestId, requestId)
                        .eq(ServiceRequestLogEntity::getAction, RequestTransitions.Action.URGE.name())
                        .orderByDesc(ServiceRequestLogEntity::getCreatedAt)
                        .last("LIMIT 1"));
        if (lastUrge != null && lastUrge.getCreatedAt() != null
                && lastUrge.getCreatedAt().isAfter(now.minusHours(URGE_COOLDOWN_HOURS))) {
            long waitSeconds = URGE_COOLDOWN_HOURS * 3600
                    - java.time.Duration.between(lastUrge.getCreatedAt(), now).getSeconds();
            return new UrgeResponse(requestId, false, Math.max(waitSeconds, 1),
                    "已收到您的催单，请稍后再试");
        }

        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.URGE,
                from, from, requester.userId(), requester.displayName(), "用户催单");
        int sent = notifyAssigneeForUrge(entity);
        log.info("服务申请已催单, requestId={}, requesterId={}", requestId, requester.userId());
        return new UrgeResponse(requestId, true, URGE_COOLDOWN_HOURS * 3600L,
                sent == 0 ? "已记录催办，当前暂无可通知的受理人员"
                        : RequestBusinessView.leave(entity) ? "已提醒请假审批受理人员"
                        : RequestBusinessView.repair(entity) ? "已提醒维修受理人员" : "已提醒事项受理人员");
    }

    /** 催单提醒发给当前工单接单师傅；未派单或师傅无账号则跳过 */
    private int notifyAssigneeForUrge(ServiceRequestEntity entity) {
        WorkOrderEntity order = RequestBusinessView.repair(entity) ? workOrderMapper.findByRequestId(entity.getId()) : null;
        if (order == null || order.getAssigneeId() == null) {
            return routingNotifications.notifyQueue(entity, "申请催办提醒",
                    "申请「" + entity.getTitle() + "」（" + entity.getRequestNo() + "）的申请人催促尽快处理。");
        }
        notificationService.send(new NotificationSendCommand(order.getAssigneeId(), "REQUEST",
                "用户催单提醒", "申请「" + entity.getTitle() + "」（" + entity.getRequestNo() + "）的用户催促尽快处理。",
                "SERVICE_REQUEST", entity.getId()));
        return 1;
    }

    private ServiceRequestEntity requireOwnedRequest(Long requesterId, Long requestId) {
        ServiceRequestEntity entity = serviceRequestMapper.selectById(requestId);
        if (entity == null || !entity.getRequesterId().equals(requesterId)) {
            // 非本人申请不暴露存在性
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }
        return entity;
    }

    private ServiceRequestEntity requireOwnedRequestForUpdate(Long requesterId, Long requestId) {
        ServiceRequestEntity entity = serviceRequestMapper.lockById(requestId);
        if (entity == null || !requesterId.equals(entity.getRequesterId()))
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        return entity;
    }

    private ServiceRequestEntity findByIdempotencyKey(Long requesterId, String idempotencyKey) {
        return serviceRequestMapper.selectOne(new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getRequesterId, requesterId)
                .eq(ServiceRequestEntity::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private int pageNumOrDefault(Integer pageNum) {
        return pageNum == null ? 1 : pageNum;
    }

    private int pageSizeOrDefault(Integer pageSize) {
        return pageSize == null ? 10 : pageSize;
    }

    /** 按请假流程快照落请假专属列；其他流程不写。 */
    private void applyLeaveFields(ServiceRequestEntity entity, String formData) {
        if (!RequestBusinessView.leave(entity)) {
            return;
        }
        JsonNode node = parseFormData(formData);
        if (node == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请假起止日期必填");
        }
        String leaveType = text(node, "leaveType");
        LocalDate start = parseDate(text(node, "startDate"));
        LocalDate end = parseDate(text(node, "endDate"));
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请假结束日期不得早于开始日期，且起止日期必填");
        }
        entity.setLeaveType(leaveType);
        entity.setLeaveStartAt(start == null ? null : start.atStartOfDay());
        entity.setLeaveEndAt(end == null ? null : end.atTime(23, 59, 59));
        if (start != null && end != null && !end.isBefore(start)) {
            entity.setLeaveDays(BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1)
                    .setScale(1, java.math.RoundingMode.HALF_UP));
        }
        entity.setLeaveCancelStatus("NONE");
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (Exception exception) {
            return null;
        }
    }
}
