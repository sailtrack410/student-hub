package com.studenthub.modules.community.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.controller.dto.DirectMessageResponse;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityWebSocketHandler extends TextWebSocketHandler {
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RealtimeEventBus> eventBus;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("token");
        java.util.Optional<AuthenticatedUser> user = token == null ? java.util.Optional.empty() : sessionStore.find(token);
        if (user.isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid session"));
            return;
        }
        session.getAttributes().put("userId", user.get().userId());
        Set<WebSocketSession> userSessions = sessions.computeIfAbsent(user.get().userId(),
                ignored -> ConcurrentHashMap.newKeySet());
        boolean first = userSessions.isEmpty();
        userSessions.add(session);
        if (first) {
            if (eventBus.getIfAvailable() != null) eventBus.getObject().presence(user.get().userId(), true);
            broadcastPresence(user.get().userId(), true);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object value = session.getAttributes().get("userId");
        if (value instanceof Long userId) {
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(userId, userSessions);
                    if (eventBus.getIfAvailable() != null) eventBus.getObject().presence(userId, false);
                    broadcastPresence(userId, isOnline(userId));
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 上行仅处理 TYPING/PING；私信内容仍走已鉴权 REST 落库。
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            JsonNode data = root.path("data");
            Object self = session.getAttributes().get("userId");
            if (!(self instanceof Long userId)) return;
            if ("PING".equals(type)) {
                sendTo(session, Map.of("type", "PONG", "data", Map.of()));
            } else if ("TYPING".equals(type)) {
                long receiverId = data.path("receiverId").asLong(0);
                boolean typing = data.path("typing").asBoolean(false);
                if (receiverId > 0) {
                    publishEvent(receiverId, "TYPING", Map.of("fromUserId", userId, "typing", typing));
                }
            }
        } catch (Exception ignored) {
            // 非法帧直接忽略
        }
    }

    public boolean isOnline(Long userId) {
        if (eventBus.getIfAvailable() != null) return eventBus.getObject().isOnline(userId);
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && !userSessions.isEmpty();
    }

    public void publishEvent(Long recipientId, String type, Object data) {
        if (eventBus.getIfAvailable() != null) { eventBus.getObject().publish(recipientId,type,data); return; }
        deliverEvent(recipientId,type,data);
    }

    private void deliverEvent(Long recipientId, String type, Object data) {
        Set<WebSocketSession> userSessions = sessions.get(recipientId);
        if (userSessions == null || userSessions.isEmpty()) return;
        try {
            String payload = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
            for (WebSocketSession session : userSessions) {
                if (session.isOpen()) { synchronized (session) { session.sendMessage(new TextMessage(payload)); } }
            }
        } catch (IOException exception) {
            log.debug("私信事件推送失败, recipientId={}, type={}", recipientId, type, exception);
        }
    }

    @EventListener
    public void receive(RealtimeEventBus.Delivery delivery) {
        if (delivery.recipientId() == null) {
            sessions.keySet().forEach(id -> deliverEvent(id, delivery.type(), delivery.data()));
        } else if ("DIRECT_MESSAGE".equals(delivery.type())) {
            deliverMessage(delivery.recipientId(), objectMapper.convertValue(delivery.data(), DirectMessageResponse.class));
        } else deliverEvent(delivery.recipientId(), delivery.type(), delivery.data());
    }

    private void broadcastPresence(Long userId, boolean online) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("online", online);
        data.put("lastSeenAt", online ? null : java.time.LocalDateTime.now().toString());
        if (eventBus.getIfAvailable() != null) { eventBus.getObject().publish(null,"PRESENCE",data); return; }
        sessions.forEach((otherId, otherSessions) -> {
            if (otherId.equals(userId)) return;
            try {
                String payload = objectMapper.writeValueAsString(Map.of("type", "PRESENCE", "data", data));
                for (WebSocketSession session : otherSessions) {
                    if (session.isOpen()) { synchronized (session) { session.sendMessage(new TextMessage(payload)); } }
                }
            } catch (IOException ignored) { }
        });
    }

    private void sendTo(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) { synchronized (session) { session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload))); } }
        } catch (IOException ignored) { }
    }

    public void publish(Long recipientId, DirectMessageResponse message) {
        if (eventBus.getIfAvailable() != null) { eventBus.getObject().publish(recipientId,"DIRECT_MESSAGE",message); return; }
        deliverMessage(recipientId,message);
    }

    private void deliverMessage(Long recipientId, DirectMessageResponse message) {
        Set<WebSocketSession> userSessions = sessions.get(recipientId);
        if (userSessions == null || userSessions.isEmpty()) return;
        try {
            String payload = objectMapper.writeValueAsString(message);
            for (WebSocketSession session : userSessions) {
                if (session.isOpen()) { synchronized (session) { session.sendMessage(new TextMessage(payload)); } }
            }
        } catch (IOException exception) {
            log.debug("私信实时推送失败, recipientId={}", recipientId, exception);
        }
    }
}
