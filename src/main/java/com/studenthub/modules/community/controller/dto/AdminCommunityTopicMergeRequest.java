package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 话题合并请求（C06）：把源话题合并到目标话题。 */
@Schema(description = "话题合并请求")
public record AdminCommunityTopicMergeRequest(
        @Schema(description = "目标话题 ID（保留该话题）") @NotNull Long targetTopicId) {
}
