package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "广告落地页退出上报（停留/跳出）")
public record ReportAdLandingExitRequest(
        @Schema(description = "点击链路 ID") @NotBlank @Size(max = 64) String clickId,
        @Schema(description = "从落地页加载完成到退出的停留毫秒数") @NotNull Long dwellMs,
        @Schema(description = "客户端会话 ID") @Size(max = 64) String sessionId) {
}
