package com.studenthub.modules.dashboard;

import com.studenthub.modules.dashboard.service.WorkingHoursCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SLA 工作日历计算单元测试：不依赖 Spring / 数据库。
 * 口径：工作日 9:00-18:00（可配置），周末默认非工作日，节假日顺延，调休上班日按工作日。
 */
class WorkingHoursCalculatorTest {

    private final WorkingHoursCalculator calculator = new WorkingHoursCalculator(null, null);

    private WorkingHoursCalculator.WorkCalendar calendar(Map<LocalDate, Boolean> overrides) {
        return new WorkingHoursCalculator.WorkCalendar(9, 18, false, overrides);
    }

    @Test
    void sameDayWithinWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 19, 10, 0); // 周一
        assertEquals(LocalDateTime.of(2026, 10, 19, 18, 0),
                calculator.deadline(start, 8, calendar(Map.of())));
    }

    @Test
    void skipsWeekend() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 16, 17, 0); // 周五 17:00
        // 周五剩 1h，周一 9:00 起再 3h → 周一 12:00
        assertEquals(LocalDateTime.of(2026, 10, 19, 12, 0),
                calculator.deadline(start, 4, calendar(Map.of())));
    }

    @Test
    void holidayPostponesDeadline() {
        LocalDate monday = LocalDate.of(2026, 10, 19);
        LocalDateTime start = LocalDateTime.of(2026, 10, 16, 17, 0);
        // 周五剩 1h + 周一为节假日 → 周二 9:00 起 3h = 12:00
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 0),
                calculator.deadline(start, 4, calendar(Map.of(monday, false))));
    }

    @Test
    void extraWorkdayCountsAsWorkday() {
        LocalDate saturday = LocalDate.of(2026, 10, 17);
        LocalDateTime start = LocalDateTime.of(2026, 10, 16, 17, 0);
        assertTrue(calculator.isWorkday(saturday, calendar(Map.of(saturday, true))));
        assertEquals(LocalDateTime.of(2026, 10, 17, 12, 0),
                calculator.deadline(start, 4, calendar(Map.of(saturday, true))));
    }

    @Test
    void snapsBeforeWindowToStartOfDay() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 19, 7, 30);
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0),
                calculator.deadline(start, 1, calendar(Map.of())));
    }

    @Test
    void snapsAfterWindowToNextWorkday() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 19, 19, 0);
        assertEquals(LocalDateTime.of(2026, 10, 20, 10, 0),
                calculator.deadline(start, 1, calendar(Map.of())));
    }

    @Test
    void zeroOrNegativeHoursReturnsStartAndNullStartIsSafe() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 19, 10, 0);
        assertEquals(start, calculator.deadline(start, 0, calendar(Map.of())));
        assertNull(calculator.deadline(null, 4, calendar(Map.of())));
    }

    @Test
    void weekendIsNotWorkdayByDefault() {
        LocalDate saturday = LocalDate.of(2026, 10, 17);
        assertFalse(calculator.isWorkday(saturday, calendar(Map.of())));
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0),
                calculator.deadline(LocalDateTime.of(2026, 10, 17, 9, 0), 1, calendar(Map.of())));
    }
}
