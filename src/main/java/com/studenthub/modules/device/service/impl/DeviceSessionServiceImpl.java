package com.studenthub.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.Digests;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.device.controller.dto.DeviceResponse;
import com.studenthub.modules.device.entity.UserDeviceSessionEntity;
import com.studenthub.modules.device.mapper.UserDeviceSessionMapper;
import com.studenthub.modules.device.service.DeviceSessionService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.SessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备管理实现：设备指纹=SHA256(UA+clientType) 的短十六进制；
 * 登记以 (userId, deviceId) 幂等覆盖——同设备再登录只换绑 token_hash；
 * 踢线=deleteSessionByHash + 删登记行；当前设备判定=当前 tokenHash 匹配行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionServiceImpl implements DeviceSessionService {

    private static final int DEVICE_ID_LIMIT = 50;

    private final UserDeviceSessionMapper deviceMapper;
    private final SessionStore sessionStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, String rawToken, String userAgent, String clientType, String ipAddress) {
        String tokenHash = Digests.sha256Hex(rawToken);
        String deviceId = deriveDeviceId(userId, userAgent, clientType);
        UserDeviceSessionEntity existing = deviceMapper.selectOne(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, userId)
                        .eq(UserDeviceSessionEntity::getDeviceId, deviceId));
        if (existing == null) {
            UserDeviceSessionEntity entity = new UserDeviceSessionEntity();
            entity.setUserId(userId);
            entity.setDeviceId(deviceId);
            entity.setTokenHash(tokenHash);
            applyDescriptor(entity, userAgent, clientType, ipAddress);
            entity.setLastActiveAt(LocalDateTime.now());
            deviceMapper.insert(entity);
            return;
        }
        // 同设备换绑会话并刷新活跃时间：旧会话随登录流程自然失效或被新 token 顶替
        UserDeviceSessionEntity update = new UserDeviceSessionEntity();
        update.setId(existing.getId());
        update.setTokenHash(tokenHash);
        update.setIpAddress(ipAddress);
        update.setLastActiveAt(LocalDateTime.now());
        deviceMapper.updateById(update);
    }

    @Override
    public List<DeviceResponse> listDevices(AuthenticatedUser user, String currentRawToken, String currentUserAgent) {
        List<UserDeviceSessionEntity> rows = deviceMapper.selectList(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, user.userId())
                        .orderByDesc(UserDeviceSessionEntity::getLastActiveAt));
        String currentHash = currentRawToken == null ? null : Digests.sha256Hex(currentRawToken);
        return rows.stream()
                .map(row -> new DeviceResponse(row.getDeviceId(),
                        row.getDeviceName(), row.getOsType(), row.getOsVersion(), row.getIpAddress(),
                        currentHash != null && currentHash.equals(row.getTokenHash()),
                        row.getLastActiveAt()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickDevice(AuthenticatedUser user, String deviceId, String currentRawToken) {
        UserDeviceSessionEntity row = deviceMapper.selectOne(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, user.userId())
                        .eq(UserDeviceSessionEntity::getDeviceId, deviceId));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        if (currentRawToken != null && Digests.sha256Hex(currentRawToken).equals(row.getTokenHash())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前设备请走退出登录");
        }
        sessionStore.deleteSessionByHash(row.getTokenHash());
        deviceMapper.deleteById(row.getId());
        log.info("设备已下线, userId={}, deviceId={}", user.userId(), deviceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int logoutOthers(AuthenticatedUser user, String currentRawToken) {
        String currentHash = currentRawToken == null ? null : Digests.sha256Hex(currentRawToken);
        List<UserDeviceSessionEntity> rows = deviceMapper.selectList(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, user.userId()));
        int revoked = 0;
        for (UserDeviceSessionEntity row : rows) {
            if (currentHash != null && currentHash.equals(row.getTokenHash())) {
                continue;
            }
            sessionStore.deleteSessionByHash(row.getTokenHash());
            deviceMapper.deleteById(row.getId());
            revoked++;
        }
        log.info("已退出其他设备, userId={}, revoked={}", user.userId(), revoked);
        return revoked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enforceDeviceLimit(Long userId, int maxDevices) {
        if (maxDevices <= 0) {
            return 0;
        }
        List<UserDeviceSessionEntity> rows = deviceMapper.selectList(
                new LambdaQueryWrapper<UserDeviceSessionEntity>()
                        .eq(UserDeviceSessionEntity::getUserId, userId)
                        .orderByAsc(UserDeviceSessionEntity::getLastActiveAt)
                        .orderByAsc(UserDeviceSessionEntity::getId));
        int revoked = 0;
        int index = 0;
        while (rows.size() - revoked > maxDevices && index < rows.size()) {
            UserDeviceSessionEntity oldest = rows.get(index++);
            // 过期会话没有 Redis key 时 deleteSessionByHash 静默返回；登记行仍要清理。
            sessionStore.deleteSessionByHash(oldest.getTokenHash());
            deviceMapper.deleteById(oldest.getId());
            revoked++;
        }
        if (revoked > 0) {
            log.info("设备上限生效，已踢除最旧设备, userId={}, max={}, revoked={}", userId, maxDevices, revoked);
        }
        return revoked;
    }

    /** 设备指纹：UA+clientType 摘要前 24 位（稳定同设备复用同一 deviceId） */
    private String deriveDeviceId(Long userId, String userAgent, String clientType) {
        String seed = (userAgent == null ? "" : userAgent) + "|" + (clientType == null ? "" : clientType);
        return "dev_" + Digests.sha256Hex(seed).substring(0, 24);
    }

    /** UA 粗解析：设备名取前段，os 按关键词识别（iPhone/Android/macOS/Windows…） */
    private void applyDescriptor(UserDeviceSessionEntity entity, String userAgent, String clientType, String ip) {
        String ua = userAgent == null ? "" : userAgent;
        entity.setDeviceName(resolveDeviceName(ua));
        String[] os = resolveOs(ua);
        entity.setOsType(os[0]);
        entity.setOsVersion(os[1]);
        entity.setIpAddress(trim(ip, 64));
        entity.setClientType(trim(clientType, 16));
    }

    private String resolveDeviceName(String ua) {
        if (ua.isBlank()) {
            return "未知设备";
        }
        String name = ua.indexOf('(') > 0 ? ua.substring(0, ua.indexOf('(')).trim() : ua.trim();
        if (name.contains("okhttp")) {
            name = "Android 客户端";
        }
        return trim(name, DEVICE_ID_LIMIT);
    }

    private String[] resolveOs(String ua) {
        if (ua.contains("iPhone")) return new String[]{"iOS", segment(ua, "iPhone OS ", " ")};
        if (ua.contains("iPad")) return new String[]{"iPadOS", segment(ua, "OS ", " ")};
        if (ua.contains("Android")) return new String[]{"Android", segment(ua, "Android ", ";")};
        if (ua.contains("Macintosh") || ua.contains("Mac OS")) return new String[]{"macOS", ""};
        if (ua.contains("Windows")) return new String[]{"Windows", segment(ua, "Windows NT ", ";")};
        if (ua.contains("Linux")) return new String[]{"Linux", ""};
        return new String[]{"其他", ""};
    }

    private String segment(String ua, String marker, String terminator) {
        int start = ua.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = ua.indexOf(terminator, start);
        String value = end < 0 ? ua.substring(start) : ua.substring(start, end);
        return trim(value, 32);
    }

    private String trim(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
