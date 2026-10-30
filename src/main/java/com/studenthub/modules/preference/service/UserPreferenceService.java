package com.studenthub.modules.preference.service;

import com.studenthub.modules.preference.controller.dto.UpdateUserPreferenceRequest;
import com.studenthub.modules.preference.controller.dto.UserPreferenceResponse;

public interface UserPreferenceService {
    UserPreferenceResponse get(Long userId);
    UserPreferenceResponse update(Long userId, UpdateUserPreferenceRequest request);
}
