package com.studenthub.modules.community.controller.dto;

import java.util.List;

/**
 * 作者主页（2026-10-17 扩展，API需求清单 5）：等级与经验实时重算，
 * wornBadgeCode 取佩戴列（未佩戴为 null）；关注体系（isFollowing）属 Phase 3，
 * 关注表落地后接入，当前恒为 false。
 */
public record CommunityAuthorProfileResponse(Long userId, String displayName, String avatarUrl,
                                             Integer level, String levelName, Integer totalExp,
                                             String wornBadgeCode,
                                             Boolean isFollowing,
                                             String college, String major, String className, String gender,
                                             List<CommunityPostResponse> posts) {
}
