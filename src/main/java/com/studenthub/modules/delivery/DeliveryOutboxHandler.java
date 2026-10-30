package com.studenthub.modules.delivery;

import com.fasterxml.jackson.databind.JsonNode;

/** 消费者必须以 eventKey 或 payload 中的业务 ID 去重，允许租约到期后的至少一次重放。 */
public interface DeliveryOutboxHandler {
    boolean supports(String topic);
    void deliver(String eventKey, String topic, Long recipientId, JsonNode payload);
}
