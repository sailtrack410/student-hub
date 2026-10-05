package com.studenthub.modules.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录设备登记（API需求清单 3.x）：一设备一行，token_hash 定位会话踢线。
 */
@Data
@TableName("user_device_session")
public class UserDeviceSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String deviceId;
    private String tokenHash;
    private String deviceName;
    private String osType;
    private String osVersion;
    private String ipAddress;
    private String clientType;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}
