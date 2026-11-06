package com.studenthub.modules.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.dashboard.controller.dto.AnalyticsOverviewResponse;
import com.studenthub.modules.dashboard.mapper.AnalyticsMapper;
import com.studenthub.modules.dashboard.service.AnalyticsService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.servicerequest.entity.ServiceRequestEntity;
import com.studenthub.modules.servicerequest.mapper.ServiceRequestMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Map<String, String> REQUEST_STATUS = Map.of(
            "SUBMITTED", "待受理", "PROCESSING", "处理中", "COMPLETED", "已办结",
            "REJECTED", "已驳回", "CANCELLED", "已撤销");
    private static final Map<String, String> ORDER_STATUS = Map.of(
            "PENDING_DISPATCH", "待派单", "ASSIGNED", "已派单", "PROCESSING", "处理中",
            "ONSITE_PHOTO_RECORDED", "现场照片已记录", "PENDING_CONFIRMATION", "待确认",
            "COMPLETED", "已完成", "REJECTED", "已驳回", "CANCELLED", "已撤销");
    private static final Map<String, String> REPORT_STATUS = Map.of(
            "PENDING", "待处理", "ACCEPTED", "已受理", "REJECTED", "已驳回");
    private static final Map<String, String> IDENTITY = Map.of(
            "STUDENT", "学生", "TEACHER", "教师", "WORKER", "服务人员", "ADMIN", "管理员");

    private final AnalyticsMapper analyticsMapper;
    private final ServiceRequestMapper requestMapper;
    private final WorkOrderMapper workOrderMapper;
    private final CommunityPostMapper postMapper;
    private final AiConversationMapper aiConversationMapper;
    private final UserMapper userMapper;
    private final AdminCampusContextService campusContextService;

    @Override
    public AnalyticsOverviewResponse overview(int days) {
        Long campusId = campusContextService.currentCampusId();
        LocalDate end = LocalDate.now(ZONE);
        LocalDate start = end.minusDays(days - 1L);
        LocalDateTime startTs = start.atStartOfDay();
        LocalDateTime endTs = end.plusDays(1).atStartOfDay();

        long requestsCreated = requestMapper.selectCount(new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getCampusId, campusId)
                .ge(ServiceRequestEntity::getCreatedAt, startTs).lt(ServiceRequestEntity::getCreatedAt, endTs));
        long requestsCompleted = requestMapper.selectCount(new LambdaQueryWrapper<ServiceRequestEntity>()
                .eq(ServiceRequestEntity::getCampusId, campusId)
                .ge(ServiceRequestEntity::getCompletedAt, startTs).lt(ServiceRequestEntity::getCompletedAt, endTs));
        long workOrdersCreated = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                        .eq(WorkOrderEntity::getFlowArchived, false)
                        .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getCampusId, campusId)
                .ge(WorkOrderEntity::getCreatedAt, startTs).lt(WorkOrderEntity::getCreatedAt, endTs));
        long workOrdersCompleted = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                        .eq(WorkOrderEntity::getFlowArchived, false)
                        .inSql(WorkOrderEntity::getRequestId, WorkOrderMapper.REPAIR_REQUEST_IDS)
                .eq(WorkOrderEntity::getCampusId, campusId)
                .ge(WorkOrderEntity::getCompletedAt, startTs).lt(WorkOrderEntity::getCompletedAt, endTs));
        long posts = postMapper.selectCount(new LambdaQueryWrapper<CommunityPostEntity>()
                .eq(CommunityPostEntity::getCampusId, campusId)
                .ge(CommunityPostEntity::getCreatedAt, startTs).lt(CommunityPostEntity::getCreatedAt, endTs));
        long comments = analyticsMapper.countComments(campusId, startTs, endTs);
        long newUsers = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCampusId, campusId)
                .ge(UserEntity::getCreatedAt, startTs).lt(UserEntity::getCreatedAt, endTs));
        long aiSessions = aiConversationMapper.selectCount(new LambdaQueryWrapper<AiConversationEntity>()
                .ge(AiConversationEntity::getCreatedAt, startTs).lt(AiConversationEntity::getCreatedAt, endTs)
                .inSql(AiConversationEntity::getUserId, "SELECT id FROM sys_user WHERE deleted = 0 AND campus_id = " + campusId));
        long aiMessages = analyticsMapper.countAiMessages(campusId, startTs, endTs);
        long activeUsers = analyticsMapper.countActiveUsers(campusId, startTs, endTs);

        AnalyticsOverviewResponse.Kpis kpis = new AnalyticsOverviewResponse.Kpis(
                requestsCreated, requestsCompleted, workOrdersCreated, workOrdersCompleted,
                posts, comments, newUsers, activeUsers, aiSessions, aiMessages);

        // 按天趋势：先按日期补零，再回填 5 条序列
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            buckets.put(start.plusDays(i), new long[]{0, 0, 0, 0, 0});
        }
        fillByDay(buckets, analyticsMapper.requestsByDay(campusId, startTs, endTs), 0);
        fillByDay(buckets, analyticsMapper.workOrdersByDay(campusId, startTs, endTs), 1);
        fillByDay(buckets, analyticsMapper.postsByDay(campusId, startTs, endTs), 2);
        fillByDay(buckets, analyticsMapper.newUsersByDay(campusId, startTs, endTs), 3);
        fillByDay(buckets, analyticsMapper.aiSessionsByDay(campusId, startTs, endTs), 4);
        List<AnalyticsOverviewResponse.TrendPoint> trend = buckets.entrySet().stream()
                .map(e -> new AnalyticsOverviewResponse.TrendPoint(e.getKey().toString(),
                        e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4]))
                .toList();

        return new AnalyticsOverviewResponse(start.toString(), end.toString(), days, kpis, trend,
                slices(analyticsMapper.requestStatusSlices(campusId, startTs, endTs), REQUEST_STATUS),
                slices(analyticsMapper.workOrderStatusSlices(campusId, startTs, endTs), ORDER_STATUS),
                slices(analyticsMapper.topServiceSlices(campusId, startTs, endTs), Map.of()),
                slices(analyticsMapper.topSectionSlices(campusId, startTs, endTs), Map.of()),
                slices(analyticsMapper.userIdentitySlices(campusId, startTs, endTs), IDENTITY),
                slices(analyticsMapper.reportStatusSlices(campusId, startTs, endTs), REPORT_STATUS));
    }

    private void fillByDay(Map<LocalDate, long[]> buckets, List<Map<String, Object>> rows, int index) {
        for (Map<String, Object> row : rows) {
            LocalDate day = toDate(value(row, "day"));
            if (day == null || !buckets.containsKey(day)) {
                continue;
            }
            buckets.get(day)[index] += toLong(value(row, "total"));
        }
    }

    private List<AnalyticsOverviewResponse.Slice> slices(List<Map<String, Object>> rows,
                                                         Map<String, String> labels) {
        long sum = rows.stream().mapToLong(row -> toLong(value(row, "total"))).sum();
        return rows.stream().map(row -> {
            String name = String.valueOf(value(row, "name"));
            long count = toLong(value(row, "total"));
            double percent = sum == 0 ? 0 : Math.round(count * 1000.0 / sum) / 10.0;
            return new AnalyticsOverviewResponse.Slice(labels.getOrDefault(name, name), count, percent);
        }).toList();
    }

    /** H2 返回大写列名，MySQL 保持别名；统一按小写匹配。 */
    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private LocalDate toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        String text = String.valueOf(value);
        return text.length() >= 10 ? LocalDate.parse(text.substring(0, 10)) : null;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return 0;
        }
    }
}
