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
        SUBMIT, APPROVE, ACCEPT, LEAVE_CANCEL, DISPATCH, START, ARRIVAL_PHOTO, COMPLETION_PHOTO, COMPLETE, CONFIRM, REOPEN, REJECT, CANCEL, EVALUATE,
        /** 用户催单：纯记录型动作，不改变状态；2 小时限频由业务层实现 */
        URGE
    }

    private static final Map<Action, Set<Status>> ALLOWED_FROM = Map.ofEntries(
            Map.entry(Action.SUBMIT, Set.of()),
            Map.entry(Action.APPROVE, Set.of(Status.SUBMITTED)),
            Map.entry(Action.ACCEPT, Set.of(Status.SUBMITTED)),
            Map.entry(Action.LEAVE_CANCEL, Set.of(Status.PROCESSING)),
            Map.entry(Action.DISPATCH, Set.of(Status.SUBMITTED)),
            Map.entry(Action.START, Set.of(Status.PROCESSING)),
            Map.entry(Action.ARRIVAL_PHOTO, Set.of(Status.PROCESSING)),
            Map.entry(Action.COMPLETION_PHOTO, Set.of(Status.PROCESSING)),
            Map.entry(Action.COMPLETE, Set.of(Status.PROCESSING)),
            Map.entry(Action.CONFIRM, Set.of(Status.PROCESSING)),
            Map.entry(Action.REOPEN, Set.of(Status.PROCESSING)),
            Map.entry(Action.REJECT, Set.of(Status.SUBMITTED, Status.PROCESSING)),
            Map.entry(Action.CANCEL, Set.of(Status.SUBMITTED, Status.PROCESSING)),
            Map.entry(Action.EVALUATE, Set.of(Status.COMPLETED)),
            Map.entry(Action.URGE, Set.of(Status.SUBMITTED, Status.PROCESSING)));

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
                case APPROVE, ACCEPT, DISPATCH, START, ARRIVAL_PHOTO, COMPLETION_PHOTO, CONFIRM, REOPEN -> Status.PROCESSING;
                case COMPLETE, LEAVE_CANCEL -> Status.COMPLETED;
                case REJECT -> Status.REJECTED;
                case CANCEL -> Status.CANCELLED;
                case SUBMIT, EVALUATE, URGE -> current;
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
