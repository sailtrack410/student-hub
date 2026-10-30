package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务人员负载")
public record WorkloadItemResponse(
        @Schema(description = "服务人员 ID") Long userId,
        @Schema(description = "账号") String username,
        @Schema(description = "姓名") String displayName,
        @Schema(description = "手机号（脱敏）", nullable = true) String phoneMasked,
        @Schema(description = "所属组织 ID", nullable = true) Long organizationId,
        @Schema(description = "所属组织名称", nullable = true) String organizationName,
        @Schema(description = "进行中工单数") long activeCount,
        @Schema(description = "其中已超时工单数") long overdueCount,
        @Schema(description = "其中即将超时工单数") long dueSoonCount,
        @Schema(description = "其中待申请人确认工单数") long pendingConfirmationCount) {
}
