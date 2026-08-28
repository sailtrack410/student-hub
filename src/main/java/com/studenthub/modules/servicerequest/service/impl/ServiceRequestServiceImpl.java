package com.studenthub.modules.servicerequest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicecatalog.service.ServiceCatalogService;
import com.studenthub.modules.servicerequest.controller.dto.RequestLogResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestDetailResponse;
import com.studenthub.modules.servicerequest.controller.dto.ServiceRequestSummaryResponse;
import com.studenthub.modules.servicerequest.controller.dto.SubmitServiceRequestRequest;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRequestServiceImpl implements ServiceRequestService {

    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ServiceRequestMapper serviceRequestMapper;
    private final ServiceRequestLogMapper requestLogMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final RequestLogRecorder requestLogRecorder;
    private final ServiceCatalogService serviceCatalogService;
    private final WorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceRequestDetailResponse submit(AuthenticatedUser requester, SubmitServiceRequestRequest request,
                                               String idempotencyKey) {
        ServiceItemEntity item = serviceCatalogService.requireEnabledItem(request.itemId());
        String normalizedFormData = serviceCatalogService.validateAndNormalizeFormData(
                request.itemId(), request.formData());

        ServiceRequestEntity entity = new ServiceRequestEntity();
        entity.setRequestNo("SR-" + LocalDate.now().format(REQUEST_NO_DATE) + "-TMP");
        entity.setCampusId(item.getCampusId());
        entity.setItemId(item.getId());
        entity.setItemName(item.getItemName());
        entity.setCategoryId(item.getCategoryId());
        entity.setRequesterId(requester.userId());
        entity.setTitle(request.title() == null || request.title().isBlank()
                ? item.getItemName() : request.title().trim());
        entity.setFormData(normalizedFormData);
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
        requestNoUpdate.setRequestNo("SR-" + LocalDate.now().format(REQUEST_NO_DATE) + "-" + entity.getId());
        requestNoUpdate.setVersion(entity.getVersion());
        serviceRequestMapper.updateById(requestNoUpdate);
        entity.setRequestNo(requestNoUpdate.getRequestNo());

        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.SUBMIT,
                null, RequestTransitions.Status.SUBMITTED, requester.userId(), requester.displayName(),
                "提交申请");
        workOrderService.createPendingForRequest(entity);

        notificationService.send(new NotificationSendCommand(requester.userId(), "REQUEST",
                "申请已受理", "你的申请「" + entity.getTitle() + "」已提交成功，等待调度分配。",
                "SERVICE_REQUEST", entity.getId()));
        log.info("服务申请已提交, requestId={}, itemId={}, requesterId={}",
                entity.getId(), item.getId(), requester.userId());
        return detail(requester, entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceRequestDetailResponse cancel(AuthenticatedUser requester, Long requestId, String reason) {
        ServiceRequestEntity entity = requireOwnedRequest(requester.userId(), requestId);
        RequestTransitions.Status from = RequestTransitions.parse(entity.getStatus());
        RequestTransitions.Status to = RequestTransitions.target(RequestTransitions.Action.CANCEL, from);

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(entity.getId());
        update.setStatus(to.name());
        update.setCancelReason(reason);
        update.setVersion(entity.getVersion());
        serviceRequestMapper.updateById(update);
        entity.setStatus(to.name());

        workOrderService.cancelForRequest(entity, requester, reason);
        requestLogRecorder.record(entity.getId(), null, RequestTransitions.Action.CANCEL,
                from, to, requester.userId(), requester.displayName(), reason);
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
        return PageResult.of(page.convert(ServiceRequestSummaryResponse::from));
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

        WorkOrderEntity order = workOrderMapper.findByRequestId(entity.getId());
        WorkOrderSummaryResponse workOrder = order == null ? null : WorkOrderSummaryResponse.from(order);

        ServiceEvaluationEntity evaluation = evaluationMapper.selectByRequestId(entity.getId());
        EvaluationResponse evaluationResponse = evaluation == null ? null : EvaluationResponse.from(evaluation);

        List<RequestLogResponse> timeline = requestLogMapper.selectList(
                        new LambdaQueryWrapper<ServiceRequestLogEntity>()
                                .eq(ServiceRequestLogEntity::getRequestId, entity.getId())
                                .orderByAsc(ServiceRequestLogEntity::getCreatedAt))
                .stream().map(RequestLogResponse::from).toList();

        return ServiceRequestDetailResponse.from(entity, itemIcon, slaHours,
                parseFormData(entity.getFormData()), workOrder, evaluationResponse, timeline);
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

    private ServiceRequestEntity requireOwnedRequest(Long requesterId, Long requestId) {
        ServiceRequestEntity entity = serviceRequestMapper.selectById(requestId);
        if (entity == null || !entity.getRequesterId().equals(requesterId)) {
            // 非本人申请不暴露存在性
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }
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
}
