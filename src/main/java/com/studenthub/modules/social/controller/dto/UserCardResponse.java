package com.studenthub.modules.social.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 用户轻量级身份名片（需求清单 2.1）：气泡弹层直出，含经验条/关注态/勋章 4 格预览。
 */
@Schema(description = "用户身份名片")
public record UserCardResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "展示学号/工号") String uid,
        @Schema(description = "昵称") String displayName,
        @Schema(description = "头像 URL") String avatarUrl,
        @Schema(description = "性别") String gender,
        @Schema(description = "身份类型") String identityType,
        @Schema(description = "是否官方账号") boolean official,
        @Schema(description = "官方头衔") String officialTitle,
        @Schema(description = "等级") Integer level,
        @Schema(description = "等级名") String levelName,
        @Schema(description = "累计经验") Integer currentExp,
        @Schema(description = "下一级经验") Integer nextLevelExp,
        @Schema(description = "我是否关注了 TA") boolean isFollowing,
        @Schema(description = "是否互相关注") boolean isMutualFollowing,
        @Schema(description = "已获勋章数") long unlockedBadgeCount,
        @Schema(description = "勋章总数") long totalBadgeCount,
        @Schema(description = "勋章预览（最多 4 枚，showMedals=false 时为空）") List<BadgePreview> previewBadges) {

    @Schema(description = "勋章预览项")
    public record BadgePreview(
            @Schema(description = "勋章编码") String badgeCode,
            @Schema(description = "勋章名") String name,
            @Schema(description = "图标路径") String iconUrl) {
    }
}
