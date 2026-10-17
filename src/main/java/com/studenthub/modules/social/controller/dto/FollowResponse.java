package com.studenthub.modules.social.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 关注动作结果（需求清单 2.3）。
 */
@Schema(description = "关注操作结果")
public record FollowResponse(
        @Schema(description = "目标用户 ID") Long targetUserId,
        @Schema(description = "当前是否已关注") boolean following,
        @Schema(description = "是否互相关注") boolean isMutualFollowing) {
}
