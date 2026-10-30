package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.notification.mapper.NotificationMapper;
import com.studenthub.modules.notification.service.NotificationPushDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 读取原事务已保存的唯一通知，不在重试时重复插入通知。 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxHandler implements DeliveryOutboxHandler {
    private final NotificationMapper notifications;
    private final NotificationPushDispatcher dispatcher;
    @Override public boolean supports(String topic) { return "NOTIFICATION".equals(topic); }
    @Override public void deliver(String eventKey, String topic, Long recipientId, JsonNode payload) {
        var entity = notifications.selectById(payload.path("notificationId").asLong());
        if (entity != null && recipientId.equals(entity.getRecipientId())) dispatcher.dispatch(entity);
    }
}
