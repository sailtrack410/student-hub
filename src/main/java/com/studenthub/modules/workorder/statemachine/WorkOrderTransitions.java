package com.studenthub.modules.workorder.statemachine;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：PENDING_DISPATCH → ASSIGNED → PROCESSING → COMPLETED；
 * REJECTED（调度驳回）与 CANCELLED（申请人撤销）为终态。
 */
public final class WorkOrderTransitions {

    public enum Status {
        PENDING_DISPATCH, ASSIGNED, PROCESSING, COMPLETED, REJECTED, CANCELLED
    }

    public enum Action {
        ASSIGN, START, COMPLETE, REJECT, CANCEL
    }

    private static final Map<Action, Set<Status>> ALLOWED_FROM = Map.of(
            Action.ASSIGN, Set.of(Status.PENDING_DISPATCH, Status.ASSIGNED),
            Action.START, Set.of(Status.ASSIGNED),
            Action.COMPLETE, Set.of(Status.PROCESSING),
            Action.REJECT, Set.of(Status.PENDING_DISPATCH, Status.ASSIGNED),
            Action.CANCEL, Set.of(Status.PENDING_DISPATCH, Status.ASSIGNED, Status.PROCESSING));

    private WorkOrderTransitions() {
    }

    public static Status target(Action action, Status current) {
        if (ALLOWED_FROM.get(action).contains(current)) {
            return switch (action) {
                case ASSIGN -> Status.ASSIGNED;
                case START -> Status.PROCESSING;
                case COMPLETE -> Status.COMPLETED;
                case REJECT -> Status.REJECTED;
                case CANCEL -> Status.CANCELLED;
            };
        }
        throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
    }

    public static Status parse(String status) {
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "未知的工单状态：" + status);
        }
    }
}
