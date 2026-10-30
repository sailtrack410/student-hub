package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 话题统计（C06）：关联帖子数、近 7 天新增、推荐状态与合并去向。 */
@Schema(description = "话题统计")
public record AdminCommunityTopicStatsResponse(
        @Schema(description = "话题 ID") Long topicId,
        @Schema(description = "话题名") String name,
        @Schema(description = "关联帖子总数（按帖子 topics 标签匹配）") long postCount,
        @Schema(description = "近 7 天新增帖子数") long recentPostCount,
        @Schema(description = "是否推荐位") boolean recommended,
        @Schema(description = "已合并到的话题 ID（未合并为 null）") Long mergedIntoId) {
}
