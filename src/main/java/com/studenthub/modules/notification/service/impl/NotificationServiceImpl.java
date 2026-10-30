package com.studenthub.modules.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.notification.controller.dto.MyNotificationQuery;
import com.studenthub.modules.notification.controller.dto.NotificationResponse;
import com.studenthub.modules.notification.entity.NotificationEntity;
import com.studenthub.modules.notification.mapper.NotificationMapper;
import com.studenthub.modules.notification.service.NotificationService;
import com.studenthub.modules.notification.service.dto.NotificationSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final com.studenthub.modules.delivery.DeliveryOutboxService outbox;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(NotificationSendCommand command) {
        NotificationEntity entity = new NotificationEntity();
        entity.setRecipientId(command.recipientId());
        entity.setNotificationType(command.notificationType());
        entity.setTitle(command.title());
        entity.setContent(command.content());
        entity.setRelatedType(command.relatedType());
        entity.setRelatedId(command.relatedId());
        notificationMapper.insert(entity);
        outbox.enqueue("notification:" + entity.getId(), "NOTIFICATION", entity.getRecipientId(),
                java.util.Map.of("notificationId", entity.getId()));
        log.info("站内通知已发送, recipientId={}, type={}, title={}",
                command.recipientId(), command.notificationType(), command.title());
    }

    @Override
    public PageResult<NotificationResponse> pageMy(Long recipientId, MyNotificationQuery query) {
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientId, recipientId)
                .eq(query.unreadOnly() != null && query.unreadOnly(), NotificationEntity::getReadAt, null)
                .like(query.communityOnly() != null && query.communityOnly(), NotificationEntity::getNotificationType, "COMMUNITY_")
                .notLike(query.communityOnly() == null || !query.communityOnly(), NotificationEntity::getNotificationType, "COMMUNITY_")
                .orderByDesc(NotificationEntity::getCreatedAt);
        Page<NotificationEntity> page = notificationMapper.selectPage(
                new Page<>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        return PageResult.of(page.convert(NotificationResponse::from));
    }

    @Override
    public long unreadCount(Long recipientId, boolean communityOnly) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientId, recipientId)
                .like(communityOnly, NotificationEntity::getNotificationType, "COMMUNITY_")
                .notLike(!communityOnly, NotificationEntity::getNotificationType, "COMMUNITY_")
                .isNull(NotificationEntity::getReadAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long recipientId, Long notificationId) {
        NotificationEntity entity = notificationMapper.selectById(notificationId);
        if (entity == null || !entity.getRecipientId().equals(recipientId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (entity.getReadAt() != null) {
            return;
        }
        NotificationEntity update = new NotificationEntity();
        update.setId(entity.getId());
        update.setReadAt(LocalDateTime.now());
        update.setVersion(entity.getVersion());
        notificationMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long recipientId, boolean communityOnly) {
        NotificationEntity update = new NotificationEntity();
        update.setReadAt(LocalDateTime.now());
        notificationMapper.update(update, new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientId, recipientId)
                .like(communityOnly, NotificationEntity::getNotificationType, "COMMUNITY_")
                .notLike(!communityOnly, NotificationEntity::getNotificationType, "COMMUNITY_")
                .isNull(NotificationEntity::getReadAt));
    }
}
