package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "处理社区举报")
public record AdminCommunityReportHandleRequest(
        @Schema(description = "处理结论：ACCEPT 举报成立 / REJECT 举报不成立") @NotBlank String action,
        @Schema(description = "举报成立时对内容的处置：NONE 不处置 / HIDE 下架保留 / DELETE 删除；仅对 POST、COMMENT 有效", nullable = true) String contentAction,
        @Schema(description = "举报成立时的处罚：MUTE 限制发言 / BAN 禁止访问社区；不传则不处罚", nullable = true) String sanctionType,
        @Schema(description = "处罚时长（小时），不传或 0 表示长期有效", nullable = true) @Min(0) @Max(8760) Integer sanctionHours,
        @Schema(description = "处理说明，写入处理记录并展示给举报人与被处罚人", nullable = true) @Size(max = 500) String note,
        @Schema(description = "处罚/理由模板 ID（可选，自动用模板正文作为处理说明）", nullable = true) Long templateId) {
}
