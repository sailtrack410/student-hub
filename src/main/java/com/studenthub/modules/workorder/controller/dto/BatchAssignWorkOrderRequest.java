package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "批量派单请求")
public record BatchAssignWorkOrderRequest(
        @Schema(description = "待派发工单 ID 列表，单次最多 50 条")
        @NotEmpty(message = "请选择工单") @Size(max = 50, message = "单次最多批量派发 50 条工单") List<Long> orderIds,
        @Schema(description = "处理人（服务人员）ID") @NotNull(message = "处理人必填") Long assigneeId,
        @Schema(description = "派单说明", nullable = true) @Size(max = 512, message = "派单说明最长 512 字") String comment) {
}
