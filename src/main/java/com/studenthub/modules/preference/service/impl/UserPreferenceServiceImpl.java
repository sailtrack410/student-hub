package com.studenthub.modules.preference.service.impl;

import com.studenthub.modules.preference.controller.dto.UpdateUserPreferenceRequest;
import com.studenthub.modules.preference.controller.dto.UserPreferenceResponse;
import com.studenthub.modules.preference.entity.UserPreferenceEntity;
import com.studenthub.modules.preference.mapper.UserPreferenceMapper;
import com.studenthub.modules.preference.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {
    private final UserPreferenceMapper mapper;

    @Override
    public UserPreferenceResponse get(Long userId) {
        UserPreferenceEntity entity = mapper.selectById(userId);
        if (entity == null) {
            entity = defaults(userId);
            mapper.insert(entity);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferenceResponse update(Long userId, UpdateUserPreferenceRequest request) {
        UserPreferenceEntity entity = mapper.selectById(userId);
        if (entity == null) entity = defaults(userId);
        if (request.chatPush() != null) entity.setChatPush(request.chatPush());
        if (request.repairPush() != null) entity.setRepairPush(request.repairPush());
        if (request.workOrderPush() != null) entity.setWorkOrderPush(request.workOrderPush());
        if (request.chatReminder() != null) entity.setChatReminder(request.chatReminder());
        if (request.showAcademicInfo() != null) entity.setShowAcademicInfo(request.showAcademicInfo());
        if (request.showMedals() != null) entity.setShowMedals(request.showMedals());
        entity.setUpdatedAt(LocalDateTime.now());
        if (mapper.selectById(userId) == null) mapper.insert(entity); else mapper.updateById(entity);
        return toResponse(entity);
    }

    private UserPreferenceEntity defaults(Long userId) {
        UserPreferenceEntity entity = new UserPreferenceEntity();
        entity.setUserId(userId);
        entity.setChatPush(true);
        entity.setRepairPush(true);
        entity.setWorkOrderPush(true);
        entity.setChatReminder(false);
        entity.setShowAcademicInfo(true);
        entity.setShowMedals(true);
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private UserPreferenceResponse toResponse(UserPreferenceEntity entity) {
        return new UserPreferenceResponse(
                !Boolean.FALSE.equals(entity.getChatPush()),
                !Boolean.FALSE.equals(entity.getRepairPush()),
                !Boolean.FALSE.equals(entity.getWorkOrderPush()),
                Boolean.TRUE.equals(entity.getChatReminder()),
                !Boolean.FALSE.equals(entity.getShowAcademicInfo()),
                !Boolean.FALSE.equals(entity.getShowMedals()),
                entity.getUpdatedAt());
    }
}
