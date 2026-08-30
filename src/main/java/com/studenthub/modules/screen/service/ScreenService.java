package com.studenthub.modules.screen.service;

import com.studenthub.modules.screen.controller.dto.ScreenOverviewResponse;

/**
 * 公开大屏统计服务：只读聚合真实运营数据，输出已脱敏。
 */
public interface ScreenService {

    ScreenOverviewResponse overview();
}