package com.studenthub.modules.social.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 个人四维社交统计（需求清单 2.2）。
 */
@Schema(description = "四维社交统计")
public record SocialStatsResponse(
        @Schema(description = "累计获赞总数（帖子+评论）") long likesCount,
        @Schema(description = "互相关注好友数") long mutualFollowCount,
        @Schema(description = "我的关注数") long followingCount,
        @Schema(description = "我的粉丝数") long followersCount) {
}
