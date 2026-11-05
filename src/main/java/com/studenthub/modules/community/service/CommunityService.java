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

    /**
     * 分享短链码解析（D-Share）：8 位 slug 或数字 id → 帖子响应。
     * 可见性与 requirePost 同口径；无 slug 的存量帖首次访问时懒生成回写。
     */
    CommunityPostResponse byShareSlug(AuthenticatedUser user, String slugOrId);
    /** 作者删帖（需求清单 7.2）：仅原作者可删，帖子与评论同步逻辑删除，经验由重算机制自动回收 */
    void deletePost(AuthenticatedUser user, Long postId);
    CommunityCommentResponse comment(AuthenticatedUser user, Long postId, CreateCommunityCommentRequest request);
    CommunityPostResponse togglePostLike(AuthenticatedUser user, Long postId);
    CommunityCommentResponse toggleCommentLike(AuthenticatedUser user, Long commentId);
    CommunityCommentResponse pinComment(AuthenticatedUser user, Long commentId);
    void deleteComment(AuthenticatedUser user, Long commentId);
    CommunityAuthorProfileResponse authorProfile(AuthenticatedUser user, Long authorId);

    /** 收藏/取消收藏帖子（P1 服务端化）：返回收藏后的权威状态 */
    boolean toggleBookmark(AuthenticatedUser user, Long postId);

    /** 隐藏/取消隐藏帖子（P1 服务端化）：仅影响本人信息流，返回隐藏后的权威状态 */
    boolean toggleHide(AuthenticatedUser user, Long postId);

    /** 查询用户已收藏/已隐藏的帖子 id（客户端登录后合并本机存量用） */
    List<Long> listBehaviorPostIds(AuthenticatedUser user, String kind);

    /** 我的收藏分页（P1.5 管理入口）：按收藏时间倒序，已删/下架帖子以失效状态返回 */
    PageResult<CommunityBookmarkItemResponse> pageBookmarks(AuthenticatedUser user, long pageNum, long pageSize);

    /** 已隐藏列表分页（P1.5 管理入口）：支持取消隐藏（清理操作） */
    PageResult<CommunityBookmarkItemResponse> pageHidden(AuthenticatedUser user, long pageNum, long pageSize);
}
