package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeOutboxHandler implements DeliveryOutboxHandler {
    private final RealtimeDeliveryGateway gateway;
    @Override public boolean supports(String topic) { return "REALTIME".equals(topic); }
    @Override public void deliver(String eventKey, String topic, Long recipientId, JsonNode payload) {
        gateway.dispatch(recipientId, payload.path("type").asText(), payload.path("data"), eventKey);
    }
}
