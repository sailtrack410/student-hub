package com.studenthub.modules.notification.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.notification.controller.dto.MyNotificationQuery;
import com.studenthub.modules.notification.controller.dto.NotificationResponse;
import com.studenthub.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/notifications")
@Tag(name = "小程序-消息通知")
@RequiredArgsConstructor
public class AppNotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "分页查询我的通知", description = "按发送时间倒序分页返回当前用户的通知，可只看未读。",
            operationId = "pageMyNotifications")
    public Result<PageResult<NotificationResponse>> pageMy(MyNotificationQuery query) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(notificationService.pageMy(userId, query));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "查询未读通知数量", description = "返回当前用户未读通知条数。", operationId = "countUnreadNotifications")
    public Result<Long> unreadCount() {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(notificationService.unreadCount(userId));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "标记通知已读", description = "只能操作发送给当前用户的通知。", operationId = "markNotificationRead")
    public Result<Void> markRead(@PathVariable Long notificationId) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        notificationService.markRead(userId, notificationId);
        return Result.success();
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记已读", description = "将当前用户所有未读通知标记为已读。", operationId = "markAllNotificationsRead")
    public Result<Void> markAllRead() {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        notificationService.markAllRead(userId);
        return Result.success();
    }
}
