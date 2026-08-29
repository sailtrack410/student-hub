package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.DirectMessageResponse;
import com.studenthub.modules.community.controller.dto.SendDirectMessageRequest;
import com.studenthub.modules.community.controller.dto.DirectConversationResponse;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;
import com.studenthub.modules.community.mapper.CommunityDirectMessageMapper;
import com.studenthub.modules.community.service.DirectMessageService;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.file.service.FileStorageService;
import com.studenthub.modules.community.realtime.CommunityWebSocketHandler;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    private final FileStorageMapper fileStorageMapper;
    private final ObjectMapper objectMapper;
    private final CommunityWebSocketHandler webSocketHandler;

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
        entity.setImageFileIds(writeIds(imageIds));
        messageMapper.insert(entity);
        DirectMessageResponse response = toResponse(entity);
        webSocketHandler.publish(peer.getId(), response);
        return response;
    }

    @Override
    public List<DirectConversationResponse> contacts(AuthenticatedUser user) {
        Map<Long, CommunityDirectMessageEntity> latest = new LinkedHashMap<>();
        for (CommunityDirectMessageEntity message : messageMapper.contacts(user.campusId(), user.userId())) {
            Long peerId = user.userId().equals(message.getSenderId()) ? message.getReceiverId() : message.getSenderId();
            latest.putIfAbsent(peerId, message);
        }
        return latest.entrySet().stream().map(entry -> {
            UserEntity peer = userMapper.selectById(entry.getKey());
            CommunityDirectMessageEntity message = entry.getValue();
            String avatar = null;
            if (peer != null && peer.getAvatarUrl() != null) {
                try { avatar = fileStorageService.signViewPath(Long.valueOf(peer.getAvatarUrl())); }
                catch (NumberFormatException ignored) { }
            }
            return new DirectConversationResponse(entry.getKey(), peer == null ? "同学" : peer.getDisplayName(),
                    avatar, message.getContent(), message.getCreatedAt());
        }).toList();
    }

    @Override
    public long unreadCount(AuthenticatedUser user) {
        return messageMapper.unreadCount(user.campusId(), user.userId());
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
        List<Long> ids = parseIds(entity.getImageFileIds());
        UserEntity sender = userMapper.selectById(entity.getSenderId());
        String senderAvatar = null;
        if (sender != null && sender.getAvatarUrl() != null) {
            try { senderAvatar = fileStorageService.signViewPath(Long.valueOf(sender.getAvatarUrl())); }
            catch (NumberFormatException ignored) { }
        }
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

    private String writeIds(List<Long> ids) {
        if (ids.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(ids); }
        catch (Exception exception) { throw new IllegalStateException("私信图片信息序列化失败", exception); }
    }

    private List<Long> parseIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class)); }
        catch (Exception exception) { return List.of(); }
    }
}
