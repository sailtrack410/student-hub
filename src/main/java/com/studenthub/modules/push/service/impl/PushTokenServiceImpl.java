package com.studenthub.modules.push.service.impl;

import com.studenthub.modules.push.controller.dto.PushTokenResponse;
import com.studenthub.modules.push.controller.dto.RegisterPushTokenRequest;
import com.studenthub.modules.push.entity.PushTokenEntity;
import com.studenthub.modules.push.mapper.PushTokenMapper;
import com.studenthub.modules.push.service.PushTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PushTokenServiceImpl implements PushTokenService {
    private final PushTokenMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PushTokenResponse register(Long userId, RegisterPushTokenRequest request) {
        PushTokenEntity entity = mapper.findByToken(request.token());
        if (entity == null) {
            entity = new PushTokenEntity();
            entity.setUserId(userId);
            entity.setPlatform(request.platform());
            entity.setToken(request.token());
            entity.setDeviceId(request.deviceId());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setLastSeenAt(LocalDateTime.now());
            mapper.insert(entity);
        } else {
            entity.setUserId(userId);
            entity.setPlatform(request.platform());
            entity.setDeviceId(request.deviceId());
            entity.setLastSeenAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(entity);
        }
        return new PushTokenResponse(entity.getId(), entity.getPlatform(), entity.getDeviceId(), entity.getLastSeenAt());
    }

    @Override
    public void unregister(Long userId, String token) {
        PushTokenEntity entity = mapper.findByToken(token);
        if (entity != null && entity.getUserId().equals(userId)) {
            mapper.deleteById(entity.getId());
        }
    }
}
