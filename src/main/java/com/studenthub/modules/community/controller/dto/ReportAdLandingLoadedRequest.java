package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "广告落地页加载完成上报（仅 App 内可测落地类型）")
public record ReportAdLandingLoadedRequest(
        @Schema(description = "点击链路 ID") @NotBlank @Size(max = 64) String clickId,
        @Schema(description = "客户端会话 ID") @Size(max = 64) String sessionId) {
}
