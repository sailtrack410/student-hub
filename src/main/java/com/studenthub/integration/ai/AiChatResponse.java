package com.studenthub.integration.ai;

public record AiChatResponse(String content, String model, Integer promptTokens, Integer completionTokens) {
}
