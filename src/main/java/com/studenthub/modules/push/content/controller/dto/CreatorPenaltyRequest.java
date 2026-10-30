package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "创作者处罚（警告/禁言/封禁）")
public record CreatorPenaltyRequest(
        @Schema(description = "处罚类型：WARN/MUTE/BAN") @NotBlank String penaltyType,
        @Schema(description = "处罚原因") @NotBlank @Size(max = 500) String reason,
        @Schema(description = "证据链接（可选）") @Size(max = 500) String evidenceUrl,
        @Schema(description = "开始时间，缺省当前时间") LocalDateTime startAt,
        @Schema(description = "结束时间；MUTE 必填，BAN 留空表示永久") LocalDateTime endAt) {
}
