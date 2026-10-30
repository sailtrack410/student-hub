package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.controller.dto.DirectMessageResponse;
import com.studenthub.modules.community.realtime.CommunityWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketRealtimeDeliveryGateway implements RealtimeDeliveryGateway {
    private final CommunityWebSocketHandler webSocketHandler;
    private final ObjectMapper mapper;

    @Override public void dispatch(Long recipientId, String type, JsonNode payload, String eventKey) {
        try {
            if ("DIRECT_MESSAGE".equals(type)) {
                webSocketHandler.publish(recipientId, mapper.treeToValue(payload, DirectMessageResponse.class));
            } else {
                webSocketHandler.publishEvent(recipientId, type, payload);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("实时事件解析失败: " + eventKey, exception);
        }
    }
}
