package com.studenthub.modules.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.PageResult;
import com.studenthub.modules.aichat.controller.dto.AiConversationResponse;
import com.studenthub.modules.aichat.entity.AiConversationEntity;
import com.studenthub.modules.aichat.mapper.AiConversationMapper;
import com.studenthub.modules.community.controller.dto.DirectConversationResponse;
import com.studenthub.modules.community.entity.CommunityDirectMessageEntity;
import com.studenthub.modules.community.mapper.CommunityDirectMessageMapper;
import com.studenthub.modules.community.service.DirectMessageService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.message.controller.dto.InboxItemResponse;
import com.studenthub.modules.message.service.InboxService;
import com.studenthub.modules.notification.controller.dto.MyNotificationQuery;
import com.studenthub.modules.notification.controller.dto.NotificationResponse;
import com.studenthub.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一收件箱（前端需求清单 5.2）：三路会话源折叠为扁平流。
 * 口径：系统通知全部折叠为一条置顶会话（未读数=站内通知未读总数）；私信按对端展开；
 * AI 助手按会话展开（标题即会话标题）。排序按 lastTime 倒序，内存分页（单用户三路
 * 条目量级为几十条，内存聚合可控；通知增长后可改为 SQL union）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxServiceImpl implements InboxService {

    private static final String TYPE_PROCESS_NOTIFICATION = "PROCESS_NOTIFICATION";
    private static final String TYPE_DIRECT_MESSAGE = "DIRECT_MESSAGE";
    private static final String TYPE_AI_ASSISTANT = "AI_ASSISTANT";

    private final NotificationService notificationService;
    private final DirectMessageService directMessageService;
    private final CommunityDirectMessageMapper directMessageMapper;
    private final AiConversationMapper aiConversationMapper;

    @Override
    public PageResult<InboxItemResponse> inbox(AuthenticatedUser user, int pageNum, int pageSize) {
        List<InboxItemResponse> all = new ArrayList<>();
        appendNotifications(user, all);
        appendDirectMessages(user, all);
        appendAiConversations(user, all);
        all.sort((a, b) -> b.lastTime().compareTo(a.lastTime()));

        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return PageResult.of(pageNum, pageSize, all.size(), all.subList(from, to));
    }

    private void appendNotifications(AuthenticatedUser user, List<InboxItemResponse> out) {
        // 通知取最近 1 条作预览；未读数为该路未读总数（社区通知除外，社区走消息中心自有分类）
        NotificationResponse latest = notificationService.pageMy(user.userId(),
                        new MyNotificationQuery(1, 1, null, false)).getRecords().stream().findFirst().orElse(null);
        if (latest == null) {
            return;
        }
        long unread = notificationService.unreadCount(user.userId(), false);
        out.add(new InboxItemResponse("PROCESS_NOTIFICATION", TYPE_PROCESS_NOTIFICATION, null, null,
                "流程办理通知", null, latest.content(), latest.createdAt(), unread, true));
    }

    private void appendDirectMessages(AuthenticatedUser user, List<InboxItemResponse> out) {
        Map<Long, Long> unreadByPeer = directMessageMapper.unreadByPeer(user.campusId(), user.userId())
                .stream().collect(java.util.stream.Collectors.toMap(
                        row -> ((Number) row.get("senderId")).longValue(), row -> ((Number) row.get("cnt")).longValue()));
        for (DirectConversationResponse contact : directMessageService.contacts(user)) {
            out.add(new InboxItemResponse("DIRECT_MESSAGE:" + contact.peerId(), TYPE_DIRECT_MESSAGE,
                    contact.peerId(), null, contact.peerName(), contact.avatarUrl(),
                    contact.lastContent(), contact.lastAt(), unreadByPeer.getOrDefault(contact.peerId(), 0L), false));
        }
    }

    private void appendAiConversations(AuthenticatedUser user, List<InboxItemResponse> out) {
        List<AiConversationEntity> conversations = aiConversationMapper.selectList(
                new LambdaQueryWrapper<AiConversationEntity>()
                        .eq(AiConversationEntity::getUserId, user.userId())
                        .eq(AiConversationEntity::getStatus, "ACTIVE")
                        .orderByDesc(AiConversationEntity::getUpdatedAt)
                        .last("LIMIT 20"));
        for (AiConversationEntity conversation : conversations) {
            AiConversationResponse view = AiConversationResponse.from(conversation);
            out.add(new InboxItemResponse("AI_ASSISTANT:" + conversation.getId(), TYPE_AI_ASSISTANT,
                    null, view.conversationId(), view.title(), null,
                    "继续和 AI 助手小绵对话", view.createdAt(), 0, false));
        }
    }
}
