package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "治理端内容处置：下架/恢复帖子或评论")
public record AdminCommunityContentActionRequest(
        @Schema(description = "处置原因，会随站内通知告知内容作者", nullable = true) @Size(max = 500) String reason) {
}
