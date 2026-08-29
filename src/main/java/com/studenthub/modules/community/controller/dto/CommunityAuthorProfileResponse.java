package com.studenthub.modules.community.controller.dto;

import java.util.List;

public record CommunityAuthorProfileResponse(Long userId, String displayName, String avatarUrl,
                                             String college, String major, String className, String gender,
                                             List<CommunityPostResponse> posts) {
}
