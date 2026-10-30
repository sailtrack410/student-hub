package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端申诉列表项（含 SLA 时限与证据文件签名地址） */
@Schema(description = "社区申诉（管理端）")
public record AdminCommunityAppealResponse(
        Long appealId,
        Long userId,
        String userName,
        String targetType,
        Long targetId,
        String targetTitle,
        String reasonCode,
        String reason,
        String evidenceFileIds,
        @Schema(description = "证据文件短时签名地址") List<String> evidenceUrls,
        String status,
        Long handledBy,
        LocalDateTime handledAt,
        String handleNote,
        LocalDateTime createdAt,
        @Schema(description = "处理时限（due_at；为空时为 createdAt+24h）") LocalDateTime dueAt,
        @Schema(description = "是否已超时（仅待处理有意义）") boolean overdue,
        @Schema(description = "剩余处理分钟数（已超时为负）") long remainingMinutes) {
}
