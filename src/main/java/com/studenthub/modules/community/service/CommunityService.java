package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.*;
import com.studenthub.modules.identity.security.AuthenticatedUser;

import java.util.List;

public interface CommunityService {
    List<CommunitySectionResponse> sections(Long campusId);
    List<CommunityTopicResponse> hotTopics(Long campusId);
    PageResult<CommunityPostResponse> pagePosts(AuthenticatedUser user, Long sectionId, String keyword, long pageNum, long pageSize, String sort);
    CommunityPostDetailResponse detail(AuthenticatedUser user, Long postId);
    CommunityPostResponse publish(AuthenticatedUser user, CreateCommunityPostRequest request);
    /** 作者删帖（需求清单 7.2）：仅原作者可删，帖子与评论同步逻辑删除，经验由重算机制自动回收 */
    void deletePost(AuthenticatedUser user, Long postId);
    CommunityCommentResponse comment(AuthenticatedUser user, Long postId, CreateCommunityCommentRequest request);
    CommunityPostResponse togglePostLike(AuthenticatedUser user, Long postId);
    CommunityCommentResponse toggleCommentLike(AuthenticatedUser user, Long commentId);
    CommunityCommentResponse pinComment(AuthenticatedUser user, Long commentId);
    void deleteComment(AuthenticatedUser user, Long commentId);
    CommunityAuthorProfileResponse authorProfile(AuthenticatedUser user, Long authorId);
}
