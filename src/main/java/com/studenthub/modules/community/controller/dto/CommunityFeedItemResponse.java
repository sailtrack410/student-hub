package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "社区混合信息流条目：自然帖子或官方直投广告")
public record CommunityFeedItemResponse(
        @Schema(description = "条目类型：POST 自然帖子 / AD 信息流广告") String itemType,
        @Schema(description = "自然帖子，itemType=POST 时有值", nullable = true) CommunityPostResponse post,
        @Schema(description = "信息流广告，itemType=AD 时有值", nullable = true) FeedAdItemResponse ad) {

    public static CommunityFeedItemResponse ofPost(CommunityPostResponse post) {
        return new CommunityFeedItemResponse("POST", post, null);
    }

    public static CommunityFeedItemResponse ofAd(FeedAdItemResponse ad) {
        return new CommunityFeedItemResponse("AD", null, ad);
    }
}
