package com.studenthub.modules.electricity.service;

import com.studenthub.modules.electricity.controller.dto.ElectricityBillResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

public interface ElectricityService {
    ElectricityBillResponse query(AuthenticatedUser user, String sn);
}
