package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 管理端话题响应（含推荐位、合并去向与关联帖子数，V71） */
@Schema(description = "社区话题（管理端）")
public record AdminCommunityTopicResponse(
        Long topicId,
        String name,
        Integer heat,
        String status,
        @Schema(description = "是否推荐位") boolean recommended,
        @Schema(description = "已合并到的话题 ID") Long mergedIntoId,
        @Schema(description = "关联帖子总数") long postCount) {
}
