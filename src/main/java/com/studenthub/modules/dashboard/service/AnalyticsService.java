package com.studenthub.modules.dashboard.service;

import com.studenthub.modules.dashboard.controller.dto.AnalyticsOverviewResponse;

public interface AnalyticsService {

    /** days 仅支持 7 或 30（controller 已校验）。 */
    AnalyticsOverviewResponse overview(int days);
}
