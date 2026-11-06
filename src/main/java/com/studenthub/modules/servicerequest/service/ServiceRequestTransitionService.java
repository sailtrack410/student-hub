package com.studenthub.modules.servicerequest.service;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.servicerequest.statemachine.RequestTransitions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 申请单状态流转统一入口：工单域与管理端审批均经由本服务推进申请状态，
 * 其他模块不得直改申请实体。迁移合法性由 RequestTransitions 状态机裁决，
 * 非法迁移抛 REQUEST_STATE_INVALID；已处于动作目标状态视为幂等（changed=false）。
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestTransitionService {

    private final ServiceRequestMapper requestMapper;

    public TransitionResult apply(Long requestId, RequestTransitions.Action action,
                                  String reason, LocalDateTime completedAt) {
        ServiceRequestEntity request = requestMapper.selectById(requestId);
        if (request == null) {
            return new TransitionResult(null, null, null, false);
        }
        RequestTransitions.Status from = RequestTransitions.parse(request.getStatus());
        RequestTransitions.Status to;
        try {
            to = RequestTransitions.target(action, from);
        } catch (BusinessException invalid) {
            if (from == intendedTarget(action)) {
                // 重复派单等幂等场景：状态已处于动作目标，仅记录时间线
                return new TransitionResult(request, from, from, false);
            }
            throw invalid;
        }
        if (to == from) {
            return new TransitionResult(request, from, to, false);
        }

        ServiceRequestEntity update = new ServiceRequestEntity();
        update.setId(request.getId());
        update.setStatus(to.name());
        update.setRejectReason(reason);
        update.setCompletedAt(completedAt);
        update.setVersion(request.getVersion());
        if (requestMapper.updateById(update) == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "状态已变更，请刷新后重试");
        }
        return new TransitionResult(request, from, to, true);
    }

    /** 动作的期望终态，仅用于幂等判断；迁移合法性仍由状态机裁决。URGE 为纯记录型动作，不经本服务。 */
    private static RequestTransitions.Status intendedTarget(RequestTransitions.Action action) {
        return switch (action) {
            case APPROVE, ACCEPT, DISPATCH, START, ARRIVAL_PHOTO, COMPLETION_PHOTO, CONFIRM, REOPEN -> RequestTransitions.Status.PROCESSING;
            case COMPLETE, LEAVE_CANCEL, EVALUATE -> RequestTransitions.Status.COMPLETED;
            case REJECT -> RequestTransitions.Status.REJECTED;
            case CANCEL -> RequestTransitions.Status.CANCELLED;
            case SUBMIT -> RequestTransitions.Status.SUBMITTED;
            case URGE -> null;
        };
    }

    public record TransitionResult(ServiceRequestEntity request, RequestTransitions.Status from,
                                   RequestTransitions.Status to, boolean changed) {
    }
}
