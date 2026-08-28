package com.studenthub.modules.servicerequest.statemachine;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 申请单状态机：显式定义每个动作允许的迁移，禁止未列出的状态变更。
 * SUBMITTED → PROCESSING → COMPLETED / REJECTED；未完成前可 CANCELLED。
 */
public final class RequestTransitions {

    public enum Status {
        SUBMITTED, PROCESSING, COMPLETED, REJECTED, CANCELLED
    }

    public enum Action {
        SUBMIT, DISPATCH, START, COMPLETE, REJECT, CANCEL, EVALUATE
    }

    private static final Map<Action, Set<Status>> ALLOWED_FROM = Map.of(
            Action.SUBMIT, Set.of(),
            Action.DISPATCH, Set.of(Status.SUBMITTED),
            Action.START, Set.of(Status.PROCESSING),
            Action.COMPLETE, Set.of(Status.PROCESSING),
            Action.REJECT, Set.of(Status.SUBMITTED, Status.PROCESSING),
            Action.CANCEL, Set.of(Status.SUBMITTED, Status.PROCESSING),
            Action.EVALUATE, Set.of(Status.COMPLETED));

    private RequestTransitions() {
    }

    /**
     * 校验动作是否允许，返回迁移后的状态；仅记录类动作（START/EVALUATE）返回当前状态。
     */
    public static Status target(Action action, Status current) {
        if (action == Action.SUBMIT) {
            return Status.SUBMITTED;
        }
        Set<Status> allowed = ALLOWED_FROM.get(action);
        if (allowed.contains(current)) {
            return switch (action) {
                case DISPATCH, START -> Status.PROCESSING;
                case COMPLETE -> Status.COMPLETED;
                case REJECT -> Status.REJECTED;
                case CANCEL -> Status.CANCELLED;
                case SUBMIT, EVALUATE -> current;
            };
        }
        throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID);
    }

    public static Status parse(String status) {
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "未知的申请状态：" + status);
        }
    }
}
