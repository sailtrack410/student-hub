package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.*;
import com.studenthub.modules.community.service.CommunityModerationService;
import com.studenthub.modules.community.service.CommunityService;
import com.studenthub.modules.community.service.DirectMessageService;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/community")
@Tag(name = "小程序-校园社区", description = "帖子、评论、点赞、举报与私信")
@RequiredArgsConstructor
public class AppCommunityController {
    private final CommunityService communityService;
    private final CommunityModerationService communityModerationService;
    private final CurrentUserProvider currentUserProvider;
    private final DirectMessageService directMessageService;
    private final NotificationService notificationService;

    @GetMapping("/sections")
    @Operation(summary = "查询社区分区", operationId = "listCommunitySections")
    public Result<List<CommunitySectionResponse>> sections() {
        return Result.success(communityService.sections(currentUserProvider.requireCurrentUser().campusId()));
    }

    @GetMapping("/topics/hot")
    @Operation(summary = "查询热门话题", operationId = "listHotCommunityTopics")
    public Result<List<CommunityTopicResponse>> hotTopics() {
        return Result.success(communityService.hotTopics(currentUserProvider.requireCurrentUser().campusId()));
    }

    @GetMapping("/posts")
    @Operation(summary = "分页查询社区帖子", description = "sort=LATEST(默认,时间倒序)|HOT(点赞数→评论数→时间倒序)。", operationId = "pageCommunityPosts")
    public Result<PageResult<CommunityPostResponse>> posts(
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String sort) {
        return Result.success(communityService.pagePosts(currentUserProvider.requireCurrentUser(), sectionId, keyword, pageNum, pageSize, sort));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "查询帖子详情和评论", operationId = "getCommunityPostDetail")
    public Result<CommunityPostDetailResponse> detail(@PathVariable Long postId) {
        return Result.success(communityService.detail(currentUserProvider.requireCurrentUser(), postId));
    }

    @PostMapping("/posts")
    @Operation(summary = "发布社区帖子", operationId = "createCommunityPost")
    public Result<CommunityPostResponse> publish(@RequestBody @Valid CreateCommunityPostRequest request) {
        return Result.success(communityService.publish(currentUserProvider.requireCurrentUser(), request));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "发表评论", operationId = "createCommunityComment")
    public Result<CommunityCommentResponse> comment(@PathVariable Long postId,
                                                    @RequestBody @Valid CreateCommunityCommentRequest request) {
        return Result.success(communityService.comment(currentUserProvider.requireCurrentUser(), postId, request));
    }

    @PostMapping("/posts/{postId}/like")
    @Operation(summary = "点赞或取消点赞帖子", operationId = "toggleCommunityPostLike")
    public Result<CommunityPostResponse> likePost(@PathVariable Long postId) {
        return Result.success(communityService.togglePostLike(currentUserProvider.requireCurrentUser(), postId));
    }

    @PostMapping("/comments/{commentId}/like")
    @Operation(summary = "点赞或取消点赞评论", operationId = "toggleCommunityCommentLike")
    public Result<CommunityCommentResponse> likeComment(@PathVariable Long commentId) {
        return Result.success(communityService.toggleCommentLike(currentUserProvider.requireCurrentUser(), commentId));
    }

    @PostMapping("/comments/{commentId}/pin")
    @Operation(summary = "置顶或取消置顶作者评论", operationId = "pinCommunityComment")
    public Result<CommunityCommentResponse> pinComment(@PathVariable Long commentId) {
        return Result.success(communityService.pinComment(currentUserProvider.requireCurrentUser(), commentId));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "删除作者自己发布的帖子", description = "仅原作者可删；帖子与全部评论同步逻辑删除，因发帖获得的经验值按经验可回收原则自动扣减。",
            operationId = "deleteCommunityPost")
    public Result<Void> deletePost(@PathVariable Long postId) {
        communityService.deletePost(currentUserProvider.requireCurrentUser(), postId);
        return Result.success(null);
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "删除自己的评论", operationId = "deleteCommunityComment")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        communityService.deleteComment(currentUserProvider.requireCurrentUser(), commentId);
        return Result.success(null);
    }

    @PostMapping("/reports")
    @Operation(summary = "举报社区内容或用户", description = "支持举报帖子、评论、私信与用户；同一对象重复举报与每日次数超限会被拦截，举报时服务端保存内容快照以便追溯。", operationId = "createCommunityReport")
    public Result<Void> report(@RequestBody @Valid CreateCommunityReportRequest request) {
        communityModerationService.submitReport(currentUserProvider.requireCurrentUser(), request);
        return Result.success(null);
    }

    @GetMapping("/authors/{authorId}")
    @Operation(summary = "查看社区作者主页", operationId = "getCommunityAuthorProfile")
    public Result<CommunityAuthorProfileResponse> author(@PathVariable Long authorId) {
        return Result.success(communityService.authorProfile(currentUserProvider.requireCurrentUser(), authorId));
    }

    @GetMapping("/messages")
    @Operation(summary = "查询与用户的私信")
    public Result<List<DirectMessageResponse>> messages(@RequestParam Long withUserId) {
        return Result.success(directMessageService.conversation(currentUserProvider.requireCurrentUser(), withUserId));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "查询社区未读数量")
    public Result<Long> unreadCount() {
        var user = currentUserProvider.requireCurrentUser();
        return Result.success(notificationService.unreadCount(user.userId(), true) + directMessageService.unreadCount(user));
    }

    @PostMapping("/messages/read")
    @Operation(summary = "标记私信会话已读")
    public Result<Void> markMessagesRead(@RequestParam Long withUserId) {
        directMessageService.markConversationRead(currentUserProvider.requireCurrentUser(), withUserId);
        return Result.success();
    }

    @GetMapping("/messages/contacts")
    @Operation(summary = "查询私信会话列表")
    public Result<List<DirectConversationResponse>> messageContacts() {
        return Result.success(directMessageService.contacts(currentUserProvider.requireCurrentUser()));
    }

    @PostMapping("/messages")
    @Operation(summary = "发送私信")
    public Result<DirectMessageResponse> sendMessage(@RequestBody @Valid SendDirectMessageRequest request) {
        return Result.success(directMessageService.send(currentUserProvider.requireCurrentUser(), request));
    }
}
