package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "工单派发候选服务人员")
public record WorkerCandidateResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "手机号", nullable = true) String phone) {
}
