package com.studenthub.modules.workorder.statemachine;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 工单状态机：PENDING_DISPATCH → ASSIGNED → PROCESSING → ONSITE_PHOTO_RECORDED
 * → PENDING_CONFIRMATION → COMPLETED；
 * REJECTED（调度驳回）与 CANCELLED（申请人撤销）为终态。
 * 状态中文文案与「进行中」集合以本枚举为唯一来源，dashboard/screen 等聚合处不得再自建。
 */
public final class WorkOrderTransitions {

    public enum Status {
        PENDING_DISPATCH("待派单"),
        ASSIGNED("已派单"),
        PROCESSING("处理中"),
        ONSITE_PHOTO_RECORDED("现场照片已记录"),
        PENDING_CONFIRMATION("待确认"),
        COMPLETED("已完成"),
        REJECTED("已驳回"),
        CANCELLED("已撤销");

        private final String display;

        Status(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    /** 进行中状态集合（未办结的中间态），供聚合统计复用。 */
    public static Set<String> inProgressSet() {
        return Stream.of(Status.PENDING_DISPATCH, Status.ASSIGNED, Status.PROCESSING,
                        Status.ONSITE_PHOTO_RECORDED, Status.PENDING_CONFIRMATION)
                .map(Status::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    public enum Action {
        ASSIGN, START, RECORD_ARRIVAL_PHOTO, SUBMIT_COMPLETION, CONFIRM,
        REOPEN_CONFIRMATION, COMPLETE, REJECT, CANCEL
    }

    private static final Map<Action, Set<Status>> ALLOWED_FROM = Map.of(
        Action.ASSIGN, Set.of(Status.PENDING_DISPATCH, Status.ASSIGNED),
        Action.START, Set.of(Status.ASSIGNED),
        Action.RECORD_ARRIVAL_PHOTO, Set.of(Status.PROCESSING),
        Action.SUBMIT_COMPLETION, Set.of(Status.ONSITE_PHOTO_RECORDED),
        Action.CONFIRM, Set.of(Status.PENDING_CONFIRMATION),
        Action.REOPEN_CONFIRMATION, Set.of(Status.PENDING_CONFIRMATION),
        Action.COMPLETE, Set.of(Status.PROCESSING),
        Action.REJECT, Set.of(Status.PENDING_DISPATCH, Status.ASSIGNED),
        Action.CANCEL, Set.of(Status.PENDING_DISPATCH, Status.ASSIGNED, Status.PROCESSING,
                Status.ONSITE_PHOTO_RECORDED, Status.PENDING_CONFIRMATION));

    private WorkOrderTransitions() {
    }

    public static Status target(Action action, Status current) {
        if (ALLOWED_FROM.get(action).contains(current)) {
            return switch (action) {
                case ASSIGN -> Status.ASSIGNED;
                case START -> Status.PROCESSING;
                case RECORD_ARRIVAL_PHOTO -> Status.ONSITE_PHOTO_RECORDED;
                case SUBMIT_COMPLETION -> Status.PENDING_CONFIRMATION;
                case CONFIRM, COMPLETE -> Status.COMPLETED;
                case REOPEN_CONFIRMATION -> Status.ONSITE_PHOTO_RECORDED;
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
