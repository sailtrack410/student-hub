package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端-用户登录设备（U05）：设备名 / 系统 / IP / 最近活跃 / 在线状态。
 *
 * <p>{@code status} 由会话存储实时探测（ONLINE=会话仍在有效期，OFFLINE=会话已过期或已被踢下线），
 * 不是按时间猜测的伪状态。</p>
 */
@Schema(description = "管理端用户登录设备")
public record AdminUserDeviceResponse(
        @Schema(description = "设备 ID") String deviceId,
        @Schema(description = "设备名") String deviceName,
        @Schema(description = "系统类型") String osType,
        @Schema(description = "系统版本") String osVersion,
        @Schema(description = "最近 IP") String ipAddress,
        @Schema(description = "客户端类型 APP/WEB", nullable = true) String clientType,
        @Schema(description = "在线状态 ONLINE/OFFLINE") String status,
        @Schema(description = "最近活跃时间", nullable = true) LocalDateTime lastActiveAt,
        @Schema(description = "首次登记时间", nullable = true) LocalDateTime createdAt) {
}
