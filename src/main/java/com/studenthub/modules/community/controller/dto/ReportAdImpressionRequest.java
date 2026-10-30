package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "广告曝光上报")
public record ReportAdImpressionRequest(
        @Schema(description = "信息流下发的点击链路 ID；缺省则服务端生成") @Size(max = 64) String clickId,
        @Schema(description = "客户端会话 ID，用于单次会话频控") @Size(max = 64) String sessionId) {
}
