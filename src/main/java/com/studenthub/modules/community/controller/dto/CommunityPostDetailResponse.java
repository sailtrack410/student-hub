package com.studenthub.modules.community.controller.dto;

import java.util.List;

public record CommunityPostDetailResponse(CommunityPostResponse post, List<CommunityCommentResponse> comments) {
}
