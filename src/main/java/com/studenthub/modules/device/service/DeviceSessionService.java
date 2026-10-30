package com.studenthub.modules.device.service;

import com.studenthub.modules.device.controller.dto.DeviceResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;

import java.util.List;

/**
 * 设备与安全管理（API需求清单 3.x）：登录设备登记、列表、踢线、退出其他设备。
 */
public interface DeviceSessionService {

    /** 登录成功后登记设备（同设备幂等刷新 token 与活跃时间） */
    void register(Long userId, String rawToken, String userAgent, String clientType, String ipAddress);

    List<DeviceResponse> listDevices(AuthenticatedUser user, String currentRawToken, String currentUserAgent);

    /** 踢除指定设备；isCurrent 设备不允许在此下线（走 /logout） */
    void kickDevice(AuthenticatedUser user, String deviceId, String currentRawToken);

    /** 一键退出其他设备；返回踢掉的会话数 */
    int logoutOthers(AuthenticatedUser user, String currentRawToken);

    /**
     * 执行单用户同时在线设备上限：保留最新设备，踢除最旧会话与登记行。
     *
     * @param maxDevices 上限，&lt;=0 表示不限制
     * @return 被踢除的设备数
     */
    int enforceDeviceLimit(Long userId, int maxDevices);
}
