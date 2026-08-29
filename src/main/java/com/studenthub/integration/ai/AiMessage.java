package com.studenthub.integration.ai;

import java.util.List;

/**
 * 对话消息。
 *
 * @param role    system / user / assistant
 * @param content 文本内容（user 携带图片时可为空）
 * @param images  user 消息附带的图片 data URL（base64），system/assistant 为空列表
 */
public record AiMessage(String role, String content, List<String> images) {

    public AiMessage(String role, String content) {
        this(role, content, List.of());
    }

    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }
}