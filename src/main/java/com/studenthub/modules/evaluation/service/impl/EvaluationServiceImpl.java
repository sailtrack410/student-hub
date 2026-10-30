package com.studenthub.modules.evaluation.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.evaluation.controller.dto.CreateEvaluationRequest;
import com.studenthub.modules.evaluation.controller.dto.EvaluationResponse;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.evaluation.service.EvaluationService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.service.RequestLogRecorder;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final ServiceEvaluationMapper evaluationMapper;
    private final ServiceRequestMapper serviceRequestMapper;
    private final WorkOrderMapper workOrderMapper;
    private final RequestLogRecorder requestLogRecorder;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse submit(AuthenticatedUser evaluator, Long requestId, CreateEvaluationRequest request) {
        ServiceRequestEntity serviceRequest = serviceRequestMapper.selectById(requestId);
        if (serviceRequest == null || !serviceRequest.getRequesterId().equals(evaluator.userId())) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }
        RequestTransitions.target(RequestTransitions.Action.EVALUATE,
                RequestTransitions.parse(serviceRequest.getStatus()));
        if (evaluationMapper.selectByRequestId(requestId) != null) {
            throw new BusinessException(ErrorCode.EVALUATION_DUPLICATE);
        }

        WorkOrderEntity order = workOrderMapper.findByRequestId(requestId);
        ServiceEvaluationEntity entity = new ServiceEvaluationEntity();
        entity.setRequestId(requestId);
        entity.setWorkOrderId(order == null ? null : order.getId());
        entity.setEvaluatorId(evaluator.userId());
        entity.setAssigneeId(order == null ? null : order.getAssigneeId());
        entity.setAssigneeName(order == null ? null : order.getAssigneeName());
        entity.setRating(request.rating());
        entity.setTags(request.tags());
        entity.setComment(request.comment());
        evaluationMapper.insert(entity);

        ServiceRequestEntity completed = new ServiceRequestEntity();
        completed.setId(requestId);
        completed.setStatus(RequestTransitions.Status.COMPLETED.name());
        completed.setCompletedAt(java.time.LocalDateTime.now());
        completed.setVersion(serviceRequest.getVersion());
        serviceRequestMapper.updateById(completed);

        requestLogRecorder.record(requestId, order == null ? null : order.getId(),
                RequestTransitions.Action.EVALUATE, RequestTransitions.Status.COMPLETED,
                RequestTransitions.Status.COMPLETED, evaluator.userId(), evaluator.displayName(),
                "评分 " + request.rating() + " 星");

        if (order != null && order.getAssigneeId() != null) {
            notificationService.send(new NotificationSendCommand(order.getAssigneeId(), "EVALUATION",
                    "收到新的服务评价", "工单「" + order.getTitle() + "」收到 " + request.rating() + " 星评价。",
                    "SERVICE_REQUEST", requestId));
        }
        log.info("服务评价已提交, requestId={}, rating={}", requestId, request.rating());
        return EvaluationResponse.from(entity);
    }
}
