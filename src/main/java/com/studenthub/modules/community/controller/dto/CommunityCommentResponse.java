package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;

/**
 * 评论响应：weight 为热度权重（点赞 +1、举报 -3），客户端「热门」排序使用。
 * 2026-11-05 增加 authorLevel（批量组装下发真实等级，等级=f(总经验)），客户端统一展示纯 Lv.X。
 */
public record CommunityCommentResponse(Long commentId, Long parentCommentId, Long authorId, String authorName,
                                       Integer authorLevel,
                                       String avatarUrl, boolean authorOfficial, String authorOfficialTitle,
                                       String content, java.util.List<String> imageUrls, java.util.List<String> thumbnailUrls, int likeCount, int weight, boolean liked,
                                       boolean pinned, boolean author, LocalDateTime createdAt) {
}
