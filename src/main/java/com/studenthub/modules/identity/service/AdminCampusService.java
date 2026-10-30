package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.AdminCampusRequest;
import com.studenthub.modules.identity.controller.dto.AdminCampusResponse;

import java.util.List;

public interface AdminCampusService {
    List<AdminCampusResponse> list();

    AdminCampusResponse create(Long operatorId, AdminCampusRequest request);

    AdminCampusResponse update(Long operatorId, Long campusId, AdminCampusRequest request);
}
