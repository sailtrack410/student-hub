package com.studenthub.modules.dashboard;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.dashboard.controller.AdminAnalyticsController;
import com.studenthub.modules.dashboard.controller.dto.AnalyticsOverviewResponse;
import com.studenthub.modules.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 数据分析接口参数校验：days 仅允许 7/30，其余返回 400 业务异常。 */
class AdminAnalyticsControllerTest {

    private static AnalyticsOverviewResponse empty(int days) {
        return new AnalyticsOverviewResponse("2026-10-14", "2026-10-20", days,
                new AnalyticsOverviewResponse.Kpis(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void acceptsSevenAndThirtyDays() {
        AnalyticsService service = days -> empty(days);
        AdminAnalyticsController controller = new AdminAnalyticsController(service, null);
        assertEquals(7, controller.overview(7, null).getData().days());
        assertEquals(30, controller.overview(30, null).getData().days());
    }

    @Test
    void rejectsUnsupportedDays() {
        AnalyticsService service = days -> empty(days);
        AdminAnalyticsController controller = new AdminAnalyticsController(service, null);
        for (int days : new int[]{1, 14, 31, 0, -7}) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> controller.overview(days, null));
            assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
        }
    }
}
