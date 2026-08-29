package com.studenthub.modules.community.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.controller.dto.DirectMessageResponse;
import com.studenthub.modules.identity.security.SessionStore;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityWebSocketHandler extends TextWebSocketHandler {
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;
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
        sessions.computeIfAbsent(user.get().userId(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object value = session.getAttributes().get("userId");
        if (value instanceof Long userId) {
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) sessions.remove(userId, userSessions);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 消息通过已鉴权 REST 接口落库，WebSocket 仅负责实时推送。
    }

    public void publish(Long recipientId, DirectMessageResponse message) {
        Set<WebSocketSession> userSessions = sessions.get(recipientId);
        if (userSessions == null || userSessions.isEmpty()) return;
        try {
            String payload = objectMapper.writeValueAsString(message);
            for (WebSocketSession session : userSessions) {
                if (session.isOpen()) session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException exception) {
            log.debug("私信实时推送失败, recipientId={}", recipientId, exception);
        }
    }
}
