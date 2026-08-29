package com.studenthub.modules.aichat.intent;

import com.studenthub.modules.aichat.controller.dto.AiActionResponse;

import java.util.List;

/** 意图对应的确定性数据查询结果：注入 prompt 的文本 + 回复附带的动作卡片。 */
public record AiAssistantResolved(
        String contextText,
        List<AiActionResponse> actions) {

    public static AiAssistantResolved of(String contextText, List<AiActionResponse> actions) {
        return new AiAssistantResolved(contextText, actions == null ? List.of() : actions);
    }
}