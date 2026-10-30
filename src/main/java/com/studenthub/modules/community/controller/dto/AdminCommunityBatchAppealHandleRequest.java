package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 申诉批量复核请求（C04）。 */
@Schema(description = "申诉批量复核请求")
public record AdminCommunityBatchAppealHandleRequest(
        @Schema(description = "申诉 ID 列表，最多 100 条") @NotEmpty @Size(max = 100) List<Long> appealIds,
        @Schema(description = "处理结论：APPROVE 通过 / REJECT 驳回") @NotBlank String action,
        @Schema(description = "处理说明，会通知用户") @Size(max = 500) String note,
        @Schema(description = "申诉回复模板 ID（可选，填充处理说明）") Long templateId) {
}
