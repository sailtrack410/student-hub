package com.studenthub.integration.ai;

import java.util.List;

public record AiChatRequest(String model, List<AiMessage> messages) {
}
