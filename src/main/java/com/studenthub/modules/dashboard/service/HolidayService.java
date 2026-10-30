package com.studenthub.modules.dashboard.service;

import com.studenthub.modules.dashboard.controller.dto.HolidayRequest;
import com.studenthub.modules.dashboard.controller.dto.HolidayResponse;

import java.util.List;

public interface HolidayService {

    /** 按年份列出平台级 + 当前校区的节假日/调休配置。 */
    List<HolidayResponse> list(Integer year);

    HolidayResponse create(HolidayRequest request);

    HolidayResponse update(Long id, HolidayRequest request);

    void delete(Long id);
}
