package com.studenthub.modules.dashboard.service;

import com.studenthub.modules.dashboard.controller.dto.SlaOverviewResponse;

import java.time.LocalDate;

public interface SlaService {

    SlaOverviewResponse overview(LocalDate startDate, LocalDate endDate);
}
