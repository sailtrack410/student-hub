package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 举报批量处理请求（C03）。每条举报独立判定，已处理的跳过并计入 skipped。 */
@Schema(description = "举报批量处理请求")
public record AdminCommunityBatchReportHandleRequest(
        @Schema(description = "举报 ID 列表，最多 100 条") @NotEmpty @Size(max = 100) List<Long> reportIds,
        @Schema(description = "处理结论：ACCEPT 成立 / REJECT 不成立") @NotBlank String action,
        @Schema(description = "内容处置：NONE/HIDE/DELETE（仅 ACCEPT 生效）") String contentAction,
        @Schema(description = "处罚类型：MUTE/BAN（仅 ACCEPT 生效，可空）") String sanctionType,
        @Schema(description = "处罚时长（小时，0 或空表示长期有效）") Integer sanctionHours,
        @Schema(description = "处理说明，会反馈给举报人") @Size(max = 500) String note,
        @Schema(description = "处罚/理由模板 ID（可选，填充处理说明）") Long templateId) {
}
