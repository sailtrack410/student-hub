package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学校 SSO / 花名册接入状态（U06）。
 *
 * <p>学校尚未提供接口，因此默认返回 {@code NOT_CONFIGURED} 且 {@code implemented=false}，
 * 只读展示所需环境变量与对接说明，绝不返回伪造的“已同步/已接入”。</p>
 */
@Schema(description = "学校 SSO / 花名册接入状态")
public record AdminSsoStatusResponse(
        @Schema(description = "状态：NOT_CONFIGURED / CONFIGURED_PENDING_INTEGRATION") String status,
        @Schema(description = "是否已配置接入地址") boolean configured,
        @Schema(description = "是否已完成对接实现") boolean implemented,
        @Schema(description = "对接对象说明") String provider,
        @Schema(description = "需要的环境变量名") List<String> envKeys,
        @Schema(description = "状态说明") String message,
        @Schema(description = "检查时间") LocalDateTime checkedAt) {
}
