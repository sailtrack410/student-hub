package com.studenthub.modules.community.controller.dto;

import java.time.LocalDateTime;

/**
 * 收藏/隐藏列表条目（P1.5 管理入口）。
 * removed=true 表示帖子已被作者删除或被治理下架：不可点击进入，
 * 客户端据此展示失效态并提供清理（取消收藏/取消隐藏）操作。
 */
public record CommunityBookmarkItemResponse(
        Long postId,
        String title,
        String status,
        boolean removed,
        String postStatus,
        LocalDateTime actedAt) {
}
