package com.studenthub.modules.notification.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.notification.controller.dto.MyNotificationQuery;
import com.studenthub.modules.notification.controller.dto.NotificationResponse;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;

public interface NotificationService {

    void send(NotificationSendCommand command);

    PageResult<NotificationResponse> pageMy(Long recipientId, MyNotificationQuery query);

    long unreadCount(Long recipientId, boolean communityOnly);

    void markRead(Long recipientId, Long notificationId);

    void markAllRead(Long recipientId, boolean communityOnly);
}
