package com.studenthub.modules.workorder.service;

import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.statemachine.WorkOrderTransitions;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 工单 SLA 统一计算：截止时间、剩余时长、超时/即将超时判定。
 *
 * <p>截止时间优先取工单上的 due_at（创建时写入）；历史工单未回填时按 created_at + 承诺时限兜底。
 * 「进行中」集合以 {@link WorkOrderTransitions#inProgressSet()} 为唯一来源。</p>
 */
public final class WorkOrderSla {

    /** 服务项未配置 SLA 时的默认承诺时限（小时） */
    public static final int DEFAULT_SLA_HOURS = 24;

    /** 即将超时阈值（小时） */
    public static final int DUE_SOON_HOURS = 4;

    private WorkOrderSla() {
    }

    public static int effectiveSlaHours(WorkOrderEntity order) {
        Integer configured = order.getSlaHours();
        return configured == null || configured <= 0 ? DEFAULT_SLA_HOURS : configured;
    }

    public static LocalDateTime effectiveDueAt(WorkOrderEntity order) {
        if (order.getDueAt() != null) {
            return order.getDueAt();
        }
        LocalDateTime base = order.getCreatedAt() == null ? LocalDateTime.now() : order.getCreatedAt();
        return base.plusHours(effectiveSlaHours(order));
    }

    public static boolean inProgress(WorkOrderEntity order) {
        return order.getStatus() != null && WorkOrderTransitions.inProgressSet().contains(order.getStatus());
    }

    /** 剩余小时数：可为负（已超时）；终态工单返回 null。 */
    public static Double remainingHours(WorkOrderEntity order, LocalDateTime now) {
        if (!inProgress(order)) {
            return null;
        }
        return Duration.between(now, effectiveDueAt(order)).toMinutes() / 60.0;
    }

    public static boolean overdue(WorkOrderEntity order, LocalDateTime now) {
        return inProgress(order) && effectiveDueAt(order).isBefore(now);
    }

    public static boolean dueSoon(WorkOrderEntity order, LocalDateTime now) {
        if (!inProgress(order) || overdue(order, now)) {
            return false;
        }
        return !effectiveDueAt(order).isAfter(now.plusHours(DUE_SOON_HOURS));
    }

    public static Double overdueHours(WorkOrderEntity order, LocalDateTime now) {
        if (!overdue(order, now)) {
            return null;
        }
        return Duration.between(effectiveDueAt(order), now).toMinutes() / 60.0;
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
