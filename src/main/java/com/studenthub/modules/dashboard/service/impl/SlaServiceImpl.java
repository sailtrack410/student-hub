package com.studenthub.modules.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.dashboard.controller.dto.SlaOverviewResponse;
import com.studenthub.modules.dashboard.service.SlaService;
import com.studenthub.modules.dashboard.service.WorkingHoursCalculator;
import com.studenthub.modules.evaluation.entity.ServiceEvaluationEntity;
import com.studenthub.modules.evaluation.mapper.ServiceEvaluationMapper;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlaServiceImpl implements SlaService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RANGE_DAYS = 92;

    private final ServiceRequestMapper requestMapper;
    private final ServiceItemMapper itemMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final OrganizationMapper organizationMapper;
    private final AdminCampusContextService campusContextService;
    private final WorkingHoursCalculator workingHoursCalculator;

    @Override
    public SlaOverviewResponse overview(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate == null ? LocalDate.now(ZONE) : endDate;
        LocalDate start = startDate == null ? end.minusDays(29) : startDate;
        validateRange(start, end);
        Long campusId = campusContextService.currentCampusId();
        LocalDateTime startTs = start.atStartOfDay();
        LocalDateTime endTs = end.plusDays(1).atStartOfDay();

        List<ServiceRequestEntity> requests = requestMapper.selectList(new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getCampusId, campusId)
                .ge(ServiceRequestEntity::getCreatedAt, startTs)
                .lt(ServiceRequestEntity::getCreatedAt, endTs));
        // 以提交时间口径过滤（submittedAt 为空时回退 createdAt）
        requests = requests.stream().filter(r -> {
            LocalDateTime submitted = submittedAt(r);
            return submitted != null && !submitted.isBefore(startTs) && submitted.isBefore(endTs);
        }).toList();

        Set<Long> itemIds = requests.stream().map(ServiceRequestEntity::getItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> requestIds = requests.stream().map(ServiceRequestEntity::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ServiceItemEntity> items = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(ServiceItemEntity::getId, i -> i));
        Map<Long, WorkOrderEntity> orderByRequest = requestIds.isEmpty() ? Map.of()
                : workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                        .eq(WorkOrderEntity::getFlowArchived, false)
                        .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                        .in(WorkOrderEntity::getRequestId, requestIds)).stream()
                .collect(Collectors.toMap(WorkOrderEntity::getRequestId, o -> o, (a, b) -> a));
        Map<Long, ServiceEvaluationEntity> evaluationByRequest = requestIds.isEmpty() ? Map.of()
                : evaluationMapper.selectList(new LambdaQueryWrapper<ServiceEvaluationEntity>()
                        .in(ServiceEvaluationEntity::getRequestId, requestIds)).stream()
                .collect(Collectors.toMap(ServiceEvaluationEntity::getRequestId, e -> e, (a, b) -> a));
        Set<Long> orgIds = requests.stream().map(ServiceRequestEntity::getHandlerOrganizationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> orgNames = orgIds.isEmpty() ? Map.of()
                : organizationMapper.selectBatchIds(orgIds).stream()
                .collect(Collectors.toMap(OrganizationEntity::getId, OrganizationEntity::getOrganizationName));

        WorkingHoursCalculator.WorkCalendar calendar = workingHoursCalculator.calendar(campusId);
        LocalDateTime now = LocalDateTime.now(ZONE);

        long completed = 0, withSla = 0, overdue = 0, cancelled = 0, rejected = 0, rework = 0;
        List<Double> resolveHoursList = new ArrayList<>();
        List<Integer> ratings = new ArrayList<>();
        Map<String, Acc> byItem = new LinkedHashMap<>();
        Map<String, Acc> byOrg = new LinkedHashMap<>();
        List<SlaOverviewResponse.SlowOrder> slowOrders = new ArrayList<>();

        for (ServiceRequestEntity request : requests) {
            if ("CANCELLED".equals(request.getStatus())) {
                cancelled++;
            }
            if ("REJECTED".equals(request.getStatus())) {
                rejected++;
            }
            LocalDateTime submitted = submittedAt(request);
            LocalDateTime completedAt = request.getCompletedAt();
            boolean isCompleted = completedAt != null || "COMPLETED".equals(request.getStatus());
            if (isCompleted) {
                completed++;
            }
            ServiceItemEntity item = request.getItemId() == null ? null : items.get(request.getItemId());
            Integer slaHours = item == null ? null : item.getSlaHours();
            LocalDateTime deadline = slaHours != null && slaHours > 0
                    ? workingHoursCalculator.deadline(submitted, slaHours, calendar) : null;
            boolean hasSla = deadline != null && !"CANCELLED".equals(request.getStatus());
            if (hasSla) {
                withSla++;
            }
            boolean isOverdue = hasSla && (completedAt != null
                    ? completedAt.isAfter(deadline) : now.isAfter(deadline));
            if (isOverdue) {
                overdue++;
            }
            Double resolveHours = null;
            if (completedAt != null && submitted != null && completedAt.isAfter(submitted)) {
                resolveHours = Math.round(Duration.between(submitted, completedAt).toMinutes() / 6.0) / 10.0;
                resolveHoursList.add(resolveHours);
            }
            ServiceEvaluationEntity evaluation = evaluationByRequest.get(request.getId());
            Integer rating = evaluation == null ? null : evaluation.getRating();
            if (rating != null) {
                ratings.add(rating);
            }
            WorkOrderEntity order = orderByRequest.get(request.getId());
            boolean isRework = order != null && order.getConfirmationRejectReason() != null
                    && !order.getConfirmationRejectReason().isBlank();
            if (isRework) {
                rework++;
            }
            String itemName = item == null ? request.getItemName() : item.getItemName();
            String orgName = request.getHandlerOrganizationId() == null ? "未分配组织"
                    : orgNames.getOrDefault(request.getHandlerOrganizationId(),
                    "组织 #" + request.getHandlerOrganizationId());
            byItem.computeIfAbsent(itemName, k -> new Acc(request.getItemId(), itemName, slaHours))
                    .add(isCompleted, isOverdue, hasSla, resolveHours, rating, isRework);
            byOrg.computeIfAbsent(orgName, k -> new Acc(request.getHandlerOrganizationId(), orgName, null))
                    .add(isCompleted, isOverdue, hasSla, resolveHours, rating, isRework);
            if (isOverdue || (resolveHours != null && resolveHours >= 48)) {
                slowOrders.add(new SlaOverviewResponse.SlowOrder(request.getId(), request.getRequestNo(),
                        itemName, orgName, request.getStatus(),
                        submitted == null ? null : submitted.toString(),
                        deadline == null ? null : deadline.toString(), resolveHours, isOverdue));
            }
        }

        slowOrders.sort(Comparator
                .comparing(SlaOverviewResponse.SlowOrder::overdue).reversed()
                .thenComparing(o -> o.resolveHours() == null ? 0.0 : o.resolveHours(),
                        Comparator.reverseOrder()));
        if (slowOrders.size() > 12) {
            slowOrders = new ArrayList<>(slowOrders.subList(0, 12));
        }

        double overdueRate = withSla == 0 ? 0 : round1(overdue * 100.0 / withSla);
        Double avgResolve = resolveHoursList.isEmpty() ? null : round1(
                resolveHoursList.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Double avgSatisfaction = ratings.isEmpty() ? null : round1(
                ratings.stream().mapToInt(Integer::intValue).average().orElse(0));
        double reworkRate = completed == 0 ? 0 : round1(rework * 100.0 / completed);

        SlaOverviewResponse.Summary summary = new SlaOverviewResponse.Summary(
                requests.size(), completed, withSla, overdue, cancelled, overdueRate,
                avgResolve, avgSatisfaction, rework, reworkRate, rejected);

        List<SlaOverviewResponse.ServiceRow> serviceRows = byItem.values().stream()
                .map(Acc::toServiceRow)
                .sorted(Comparator.comparingLong(SlaOverviewResponse.ServiceRow::requests).reversed())
                .toList();
        List<SlaOverviewResponse.OrgRow> orgRows = byOrg.values().stream()
                .map(Acc::toOrgRow)
                .sorted(Comparator.comparingLong(SlaOverviewResponse.OrgRow::requests).reversed())
                .toList();

        SlaOverviewResponse.HolidayInfo holidayInfo = new SlaOverviewResponse.HolidayInfo(
                calendar.holidayCount(start, end), calendar.extraWorkdayCount(start, end),
                calendar.startHour(), calendar.endHour(), calendar.weekendWorkday());

        return new SlaOverviewResponse(start.toString(), end.toString(),
                "按 service_item.sla_hours（承诺工作小时）+ 工作日历（"
                        + calendar.startHour() + ":00-" + calendar.endHour() + ":00，"
                        + (calendar.weekendWorkday() ? "周末计工作日" : "周末不计工作日")
                        + "，节假日顺延）推算截止时间；工单 due_at 由工单模块按承诺时限写入并用于超时预警，看板按同一服务承诺与日历口径做跨服务项对比。",
                holidayInfo, summary, serviceRows, orgRows, slowOrders);
    }

    private LocalDateTime submittedAt(ServiceRequestEntity request) {
        return request.getSubmittedAt() != null ? request.getSubmittedAt() : request.getCreatedAt();
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "开始日期不能晚于结束日期");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "统计区间最长 92 天");
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class Acc {
        private final Long id;
        private final String name;
        private final Integer slaHours;
        private long requests;
        private long completed;
        private long withSla;
        private long overdue;
        private long rework;
        private double resolveSum;
        private long resolveCount;
        private double ratingSum;
        private long ratingCount;

        private Acc(Long id, String name, Integer slaHours) {
            this.id = id;
            this.name = name;
            this.slaHours = slaHours;
        }

        private void add(boolean isCompleted, boolean isOverdue, boolean hasSla, Double resolveHours,
                         Integer rating, boolean isRework) {
            requests++;
            if (isCompleted) completed++;
            if (hasSla) withSla++;
            if (isOverdue) overdue++;
            if (isRework) rework++;
            if (resolveHours != null) {
                resolveSum += resolveHours;
                resolveCount++;
            }
            if (rating != null) {
                ratingSum += rating;
                ratingCount++;
            }
        }

        private SlaOverviewResponse.ServiceRow toServiceRow() {
            return new SlaOverviewResponse.ServiceRow(id, name, slaHours, requests, completed, overdue,
                    withSla == 0 ? 0 : Math.round(overdue * 1000.0 / withSla) / 10.0,
                    resolveCount == 0 ? null : Math.round(resolveSum / resolveCount * 10.0) / 10.0,
                    ratingCount == 0 ? null : Math.round(ratingSum / ratingCount * 10.0) / 10.0,
                    rework);
        }

        private SlaOverviewResponse.OrgRow toOrgRow() {
            return new SlaOverviewResponse.OrgRow(id, name, requests, completed, overdue,
                    withSla == 0 ? 0 : Math.round(overdue * 1000.0 / withSla) / 10.0,
                    resolveCount == 0 ? null : Math.round(resolveSum / resolveCount * 10.0) / 10.0);
        }
    }
}
