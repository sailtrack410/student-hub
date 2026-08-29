package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.*;
import com.studenthub.modules.identity.security.AuthenticatedUser;

import java.util.List;

public interface CommunityService {
    List<CommunitySectionResponse> sections(Long campusId);
    List<CommunityTopicResponse> hotTopics(Long campusId);
    PageResult<CommunityPostResponse> pagePosts(AuthenticatedUser user, Long sectionId, String keyword, long pageNum, long pageSize);
    CommunityPostDetailResponse detail(AuthenticatedUser user, Long postId);
    CommunityPostResponse publish(AuthenticatedUser user, CreateCommunityPostRequest request);
    CommunityCommentResponse comment(AuthenticatedUser user, Long postId, CreateCommunityCommentRequest request);
    CommunityPostResponse togglePostLike(AuthenticatedUser user, Long postId);
    CommunityCommentResponse toggleCommentLike(AuthenticatedUser user, Long commentId);
    CommunityCommentResponse pinComment(AuthenticatedUser user, Long commentId);
    void deleteComment(AuthenticatedUser user, Long commentId);
    CommunityAuthorProfileResponse authorProfile(AuthenticatedUser user, Long authorId);
}
