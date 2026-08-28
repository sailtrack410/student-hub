package com.studenthub.modules.servicerequest.service;

import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.studenthub.modules.servicerequest.mapper.ServiceRequestLogMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestLogEntity;

/**
 * 申请单全生命周期时间线记录：提交、派单、开始、完成、驳回、撤销、评价都写一条日志，
 * 申请详情页时间线直接按时间读取。
 */
@Component
@RequiredArgsConstructor
public class RequestLogRecorder {

    private final ServiceRequestLogMapper logMapper;

    @Transactional(rollbackFor = Exception.class)
    public void record(Long requestId, Long workOrderId, RequestTransitions.Action action,
                       RequestTransitions.Status from, RequestTransitions.Status to,
                       Long operatorId, String operatorName, String comment) {
        ServiceRequestLogEntity entity = new ServiceRequestLogEntity();
        entity.setRequestId(requestId);
        entity.setWorkOrderId(workOrderId);
        entity.setAction(action.name());
        entity.setFromStatus(from == null ? null : from.name());
        entity.setToStatus(to == null ? null : to.name());
        entity.setOperatorId(operatorId);
        entity.setOperatorName(operatorName);
        entity.setComment(comment);
        logMapper.insert(entity);
    }
}
