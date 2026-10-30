package com.studenthub.modules.dashboard.service;

import com.studenthub.modules.dashboard.entity.ServiceHolidayEntity;
import com.studenthub.modules.dashboard.mapper.ServiceHolidayMapper;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作时间计算器：按服务时段 + 工作日历把「承诺小时数」换算成真实截止时间。
 *
 * <p>口径：service_item.sla_hours 为承诺“工作小时”；工作日默认周一至周五，
 * 服务时段由 sys_config 的 sla.workday_start_hour / sla.workday_end_hour 决定，
 * service_holiday 同时支持节假日（is_workday=false）与调休上班日（is_workday=true）。
 * 注意：本计算用于 SLA 看板与工作台超时预警；工单 due_at 由工单模块按承诺时限写入，
 * 用于工单详情/列表的超时预警，看板按服务承诺与工作日历口径做跨服务项对比。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkingHoursCalculator {

    private static final int MAX_ITERATION_DAYS = 400;

    private final ServiceHolidayMapper holidayMapper;
    private final SystemConfigService configService;

    public WorkCalendar calendar(Long campusId) {
        int startHour = intConfig("sla.workday_start_hour", campusId, 9);
        int endHour = intConfig("sla.workday_end_hour", campusId, 18);
        if (startHour < 0 || startHour > 22) {
            startHour = 9;
        }
        if (endHour <= startHour || endHour > 24) {
            endHour = 18;
        }
        boolean weekendWorkday = configService.getBoolean("sla.weekend_workday", campusId, false);
        long scopeCampus = campusId == null ? 0L : campusId;
        List<ServiceHolidayEntity> rows = holidayMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceHolidayEntity>()
                        .in(ServiceHolidayEntity::getCampusId, 0L, scopeCampus)
                        .orderByAsc(ServiceHolidayEntity::getCampusId));
        Map<LocalDate, Boolean> overrides = new HashMap<>();
        for (ServiceHolidayEntity row : rows) {
            if (row.getHolidayDate() == null) {
                continue;
            }
            // 先平台级后校区级（按 campus_id 升序），校区级自然覆盖平台级
            overrides.put(row.getHolidayDate(), Boolean.TRUE.equals(row.getIsWorkday()));
        }
        return new WorkCalendar(startHour, endHour, weekendWorkday, overrides);
    }

    /**
     * 从 start 起顺延 hours 个工作小时，返回截止时间；hours <= 0 时返回 start。
     */
    public LocalDateTime deadline(LocalDateTime start, int hours, WorkCalendar calendar) {
        if (start == null || hours <= 0) {
            return start;
        }
        long remainingMinutes = hours * 60L;
        LocalDateTime cursor = start;
        int guard = 0;
        while (remainingMinutes > 0 && guard++ < MAX_ITERATION_DAYS) {
            LocalDate day = cursor.toLocalDate();
            if (!isWorkday(day, calendar)) {
                cursor = day.plusDays(1).atTime(calendar.startHour(), 0);
                continue;
            }
            LocalDateTime dayStart = day.atTime(calendar.startHour(), 0);
            LocalDateTime dayEnd = day.atTime(calendar.endHour(), 0);
            if (cursor.isBefore(dayStart)) {
                cursor = dayStart;
            }
            if (!cursor.isBefore(dayEnd)) {
                cursor = day.plusDays(1).atTime(calendar.startHour(), 0);
                continue;
            }
            long available = ChronoUnit.MINUTES.between(cursor, dayEnd);
            if (available >= remainingMinutes) {
                return cursor.plusMinutes(remainingMinutes);
            }
            remainingMinutes -= available;
            cursor = day.plusDays(1).atTime(calendar.startHour(), 0);
        }
        return null;
    }

    public boolean isWorkday(LocalDate date, WorkCalendar calendar) {
        Boolean override = calendar.overrides().get(date);
        if (override != null) {
            return override;
        }
        int dow = date.getDayOfWeek().getValue();
        boolean weekend = dow >= 6;
        return !weekend || calendar.weekendWorkday();
    }

    private int intConfig(String key, Long campusId, int fallback) {
        String value = configService.getValue(key, campusId);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** 工作日历快照，供 SLA/工作台在单次请求内复用。 */
    public record WorkCalendar(int startHour, int endHour, boolean weekendWorkday,
                               Map<LocalDate, Boolean> overrides) {

        public int holidayCount(LocalDate start, LocalDate end) {
            return (int) overrides.entrySet().stream()
                    .filter(e -> !e.getValue() && !e.getKey().isBefore(start) && !e.getKey().isAfter(end))
                    .count();
        }

        public int extraWorkdayCount(LocalDate start, LocalDate end) {
            return (int) overrides.entrySet().stream()
                    .filter(e -> e.getValue() && !e.getKey().isBefore(start) && !e.getKey().isAfter(end))
                    .count();
        }
    }
}
