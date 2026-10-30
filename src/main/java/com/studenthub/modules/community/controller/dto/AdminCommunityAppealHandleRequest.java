package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端申诉处理请求 */
@Schema(description = "申诉处理请求")
public record AdminCommunityAppealHandleRequest(
        @Schema(description = "处理结论：APPROVE 通过并恢复 / REJECT 驳回") @NotBlank String action,
        @Schema(description = "处理说明（通知用户）") @Size(max = 500) String note,
        @Schema(description = "申诉回复模板 ID（可选，自动用模板正文作为处理说明）", nullable = true) Long templateId) {
}
