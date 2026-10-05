package com.studenthub.modules.device.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 在线设备条目（需求清单 3.1）。
 */
@Schema(description = "在线设备")
public record DeviceResponse(
        @Schema(description = "设备 ID") String deviceId,
        @Schema(description = "设备名") String deviceName,
        @Schema(description = "系统类型") String osType,
        @Schema(description = "系统版本") String osVersion,
        @Schema(description = "最近登录 IP") String ipAddress,
        @Schema(description = "是否当前设备") boolean isCurrent,
        @Schema(description = "最近活跃时间") LocalDateTime lastActiveAt) {
}
