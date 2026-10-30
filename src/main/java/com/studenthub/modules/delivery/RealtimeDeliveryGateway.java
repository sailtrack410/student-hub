package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.JsonNode;

/** 仅负责实时唤醒；消息正文与通知始终可通过原 REST 接口按 ID 补拉。 */
public interface RealtimeDeliveryGateway {
    void dispatch(Long recipientId, String type, JsonNode payload, String eventKey);
}
