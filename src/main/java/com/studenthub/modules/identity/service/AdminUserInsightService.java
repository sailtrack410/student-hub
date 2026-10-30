package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.AdminSsoStatusResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserDetailResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserDeviceResponse;

import java.util.List;

/**
 * 管理端用户运营信息（U01/U05/U06）：用户详情、登录设备、SSO/花名册接入状态。
 *
 * <p>与 {@link AdminUserService} 分开，避免继续膨胀用户分页/授权主服务；
 * 设备数据直接复用 modules/device 的登记表与会话存储，不复制一套设备体系。</p>
 */
public interface AdminUserInsightService {

    /**
     * 用户详情：资料、脱敏手机号、组织、身份、账号状态、角色绑定、官方账号、最近登录。
     *
     * @throws com.studenthub.exception.BusinessException 用户不存在或不属于当前校区
     */
    AdminUserDetailResponse detail(Long userId);

    /**
     * 登录设备列表，status 由会话存储实时探测（ONLINE/OFFLINE）。
     */
    List<AdminUserDeviceResponse> devices(Long userId);

    /**
     * 下线指定设备（删除会话 + 删除设备登记行），返回被下线的会话数。
     *
     * @throws com.studenthub.exception.BusinessException 设备不存在
     */
    int revokeDevice(Long operatorId, Long userId, String deviceId);

    /**
     * 一键下线该用户全部设备；不允许管理员对自己执行（避免误踢自己导致无法继续操作）。
     *
     * @return 被下线的会话数
     */
    int revokeAllDevices(Long operatorId, Long userId);

    /**
     * 学校 SSO / 花名册接入状态（只读，不发起任何外部请求）。
     */
    AdminSsoStatusResponse ssoStatus();
}
