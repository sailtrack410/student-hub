package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "治理端处罚用户")
public record AdminCommunitySanctionRequest(
        @Schema(description = "处罚类型：MUTE 限制发言 / BAN 禁止访问社区") @NotBlank String sanctionType,
        @Schema(description = "处罚时长（小时），不传或 0 表示长期有效", nullable = true) @Min(0) @Max(8760) Integer hours,
        @Schema(description = "处罚依据，会随站内通知告知被处罚人", nullable = true) @Size(max = 500) String reason) {
}
