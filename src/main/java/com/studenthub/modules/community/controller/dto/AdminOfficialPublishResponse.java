package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 官方代发结果（C12）。 */
@Schema(description = "官方代发帖子结果")
public record AdminOfficialPublishResponse(
        @Schema(description = "帖子 ID") Long postId,
        @Schema(description = "官方账号 ID") Long officialUserId,
        @Schema(description = "官方账号展示名") String officialName,
        @Schema(description = "帖子类型：DYNAMIC/ARTICLE") String postType,
        @Schema(description = "帖子状态") String status,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
