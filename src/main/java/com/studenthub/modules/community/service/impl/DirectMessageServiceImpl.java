package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.JsonIds;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.DirectMessageResponse;
import com.studenthub.modules.community.controller.dto.SendDirectMessageRequest;
import com.studenthub.modules.community.controller.dto.DirectConversationResponse;
import com.studenthub.modules.community.controller.dto.MessageReceiptResponse;
import com.studenthub.modules.community.controller.dto.DirectMessageReferenceCard;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;
import com.studenthub.modules.community.entity.CommunityPostEntity;
import com.studenthub.modules.community.mapper.CommunityDirectMessageMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import com.studenthub.modules.community.service.DirectMessageService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.community.realtime.CommunityWebSocketHandler;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DirectMessageServiceImpl implements DirectMessageService {
    private final CommunityDirectMessageMapper messageMapper;
    private final CommunityPostMapper postMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    private final FileStorageMapper fileStorageMapper;
    private final CommunityWebSocketHandler webSocketHandler;
    private final com.studenthub.modules.delivery.DeliveryOutboxService outbox;

    @Override
    public List<DirectMessageResponse> conversation(AuthenticatedUser user, Long withUserId) {
        requirePeer(user, withUserId);
        messageMapper.markConversationRead(user.campusId(), user.userId(), withUserId);
        return messageMapper.conversation(user.campusId(), user.userId(), withUserId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DirectMessageResponse send(AuthenticatedUser user, SendDirectMessageRequest request) {
        UserEntity peer = requirePeer(user, request.receiverId());
        String content = request.content() == null ? "" : request.content().trim();
        List<Long> imageIds = normalizeImageIds(request.imageFileIds());
        if (content.isEmpty() && imageIds.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "私信内容不能为空");
        validateOwnedImages(imageIds, user.userId());
        CommunityDirectMessageEntity entity = new CommunityDirectMessageEntity();
        entity.setCampusId(user.campusId());
        entity.setSenderId(user.userId());
        entity.setReceiverId(peer.getId());
        entity.setContent(content);
        entity.setImageFileIds(imageIds.isEmpty() ? null : JsonIds.write(imageIds));
        DirectMessageReferenceCard referenceCard = resolveReferenceCard(request, user.campusId());
        if (referenceCard != null) {
            entity.setReferenceType(referenceCard.type());
            entity.setReferenceId(referenceCard.id());
            entity.setReferenceCard(writeCard(referenceCard));
        }
        messageMapper.insert(entity);
        DirectMessageResponse response = toResponse(entity);
        outbox.enqueueRealtime("direct-message:" + entity.getId(), peer.getId(), "DIRECT_MESSAGE", response);
        return response;
    }

    /** 关联卡片：仅支持 POST，源帖须同校区且在线；快照（标题/首图）落库，源帖删除后仍可展示 */
    private DirectMessageReferenceCard resolveReferenceCard(SendDirectMessageRequest request, Long campusId) {
        if (!"POST".equalsIgnoreCase(request.referenceType()) || request.referenceId() == null) {
            return null;
        }
        CommunityPostEntity post = postMapper.selectById(request.referenceId());
        if (post == null || !campusId.equals(post.getCampusId()) || !"ENABLED".equals(post.getStatus())) {
            return null;
        }
        List<Long> firstImage = JsonIds.parse(post.getImageFileIds()).stream().limit(1).toList();
        String thumbnail = firstImage.isEmpty() ? null : fileStorageService.signThumbnailPath(firstImage.get(0));
        return new DirectMessageReferenceCard("POST", post.getId(), post.getTitle(), thumbnail);
    }

    private String writeCard(DirectMessageReferenceCard card) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(card);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<DirectConversationResponse> contacts(AuthenticatedUser user) {
        Map<Long, CommunityDirectMessageEntity> latest = new LinkedHashMap<>();
        for (CommunityDirectMessageEntity message : messageMapper.contacts(user.campusId(), user.userId())) {
            Long peerId = user.userId().equals(message.getSenderId()) ? message.getReceiverId() : message.getSenderId();
            latest.putIfAbsent(peerId, message);
        }
        Map<Long, Long> unreadByPeer = new LinkedHashMap<>();
        for (Map<String, Object> row : messageMapper.unreadByPeer(user.campusId(), user.userId())) {
            Object sender = row.get("senderId");
            Object count = row.get("cnt");
            if (sender instanceof Number s && count instanceof Number c) unreadByPeer.put(s.longValue(), c.longValue());
        }
        return latest.entrySet().stream().map(entry -> {
            UserEntity peer = userMapper.selectById(entry.getKey());
            CommunityDirectMessageEntity message = entry.getValue();
            String avatar = peer == null ? null : fileStorageService.resolveAvatarUrl(peer.getAvatarUrl());
            return new DirectConversationResponse(entry.getKey(), peer == null ? "同学" : peer.getDisplayName(),
                    avatar, message.getContent(), message.getCreatedAt(),
                    webSocketHandler.isOnline(entry.getKey()), unreadByPeer.getOrDefault(entry.getKey(), 0L));
        }).toList();
    }

    @Override
    public long unreadCount(AuthenticatedUser user) {
        return messageMapper.unreadCount(user.campusId(), user.userId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageReceiptResponse markDelivered(AuthenticatedUser user, List<Long> messageIds) {
        List<Long> updated = new java.util.ArrayList<>();
        Map<Long, List<Long>> bySender = new LinkedHashMap<>();
        for (Long id : messageIds == null ? List.<Long>of() : messageIds) {
            CommunityDirectMessageEntity entity = messageMapper.selectById(id);
            if (entity == null || !user.userId().equals(entity.getReceiverId())) continue;
            if (messageMapper.markDelivered(id, user.userId()) > 0) {
                updated.add(id);
                bySender.computeIfAbsent(entity.getSenderId(), ignored -> new java.util.ArrayList<>()).add(id);
            }
        }
        bySender.forEach((senderId, ids) -> outbox.enqueueRealtime("direct-delivered:" + java.util.UUID.randomUUID(), senderId, "DELIVERED",
                Map.of("messageIds", ids, "deliveredAt", java.time.LocalDateTime.now().toString())));
        return new MessageReceiptResponse(updated.size(), updated, java.time.LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageReceiptResponse markRead(AuthenticatedUser user, Long withUserId, List<Long> messageIds) {
        requirePeer(user, withUserId);
        if (messageIds == null || messageIds.isEmpty()) {
            messageMapper.markConversationRead(user.campusId(), user.userId(), withUserId);
            outbox.enqueueRealtime("direct-read:" + java.util.UUID.randomUUID(), withUserId, "READ",
                    Map.of("readerId", user.userId(), "readAt", java.time.LocalDateTime.now().toString()));
            return new MessageReceiptResponse(0, List.of(), java.time.LocalDateTime.now());
        }
        List<Long> updated = new java.util.ArrayList<>();
        Map<Long, List<Long>> bySender = new LinkedHashMap<>();
        for (Long id : messageIds) {
            CommunityDirectMessageEntity entity = messageMapper.selectById(id);
            if (entity == null || !user.userId().equals(entity.getReceiverId())
                    || !withUserId.equals(entity.getSenderId())) continue;
            if (messageMapper.markReadById(id, user.userId()) > 0) {
                updated.add(id);
                bySender.computeIfAbsent(entity.getSenderId(), ignored -> new java.util.ArrayList<>()).add(id);
            }
        }
        bySender.forEach((senderId, ids) -> outbox.enqueueRealtime("direct-read:" + java.util.UUID.randomUUID(), senderId, "READ",
                Map.of("readerId", user.userId(), "messageIds", ids,
                        "readAt", java.time.LocalDateTime.now().toString())));
        return new MessageReceiptResponse(updated.size(), updated, java.time.LocalDateTime.now());
    }

    @Override
    public boolean canContact(AuthenticatedUser user, Long peerId) {
        if (peerId == null || peerId.equals(user.userId())) return false;
        UserEntity peer = userMapper.selectById(peerId);
        return peer != null && user.campusId().equals(peer.getCampusId()) && "ENABLED".equals(peer.getStatus());
    }

    @Override
    public boolean isOnline(Long userId) {
        return webSocketHandler.isOnline(userId);
    }

    @Override
    public String lastSeenAt(Long userId) {
        return null;
    }

    @Override
    public void markConversationRead(AuthenticatedUser user, Long withUserId) {
        requirePeer(user, withUserId);
        messageMapper.markConversationRead(user.campusId(), user.userId(), withUserId);
    }

    private UserEntity requirePeer(AuthenticatedUser user, Long peerId) {
        if (peerId == null || user.userId().equals(peerId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不能给自己发送私信");
        }
        UserEntity peer = userMapper.selectById(peerId);
        if (peer == null || !user.campusId().equals(peer.getCampusId()) || !"ENABLED".equals(peer.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或不可联系");
        }
        return peer;
    }

    private DirectMessageResponse toResponse(CommunityDirectMessageEntity entity) {
        List<Long> ids = JsonIds.parse(entity.getImageFileIds());
        UserEntity sender = userMapper.selectById(entity.getSenderId());
        String senderAvatar = sender == null ? null : fileStorageService.resolveAvatarUrl(sender.getAvatarUrl());
        return DirectMessageResponse.from(entity, senderAvatar, fileStorageService.signViewPaths(ids),
                ids.stream().map(fileStorageService::signThumbnailPath).toList());
    }

    private List<Long> normalizeImageIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(id -> id != null && id > 0).distinct().limit(9).toList();
    }

    private void validateOwnedImages(List<Long> ids, Long userId) {
        for (Long id : ids) {
            var file = fileStorageMapper.selectById(id);
            if (file == null || !userId.equals(file.getUploaderId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能使用自己上传的图片");
            }
        }
    }
}
