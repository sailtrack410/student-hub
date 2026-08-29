package com.studenthub.modules.aichat.controller.dto;

import com.studenthub.modules.aichat.entity.AiConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 全局设置（总开关 + 超时 + 限流）；供应商与模型在供应商接口维护")
public record AiConfigResponse(
        @Schema(description = "AI 总开关") boolean enabled,
        @Schema(description = "连接超时秒") int connectTimeoutSeconds,
        @Schema(description = "读取超时秒") int readTimeoutSeconds,
        @Schema(description = "单次请求最大输入字符数") int maxInputCharacters,
        @Schema(description = "单用户每小时消息上限") int hourlyMessageLimit,
        @Schema(description = "携带历史消息条数") int historyWindow,
        @Schema(description = "单条用户消息最大字符数") int maxMessageCharacters,
        @Schema(description = "生效的默认聊天模型，未配置为 null", nullable = true) String activeChatModel) {

    public static AiConfigResponse fromEntity(AiConfigEntity entity, String activeChatModel) {
        return new AiConfigResponse(
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getMaxInputCharacters(),
                entity.getHourlyMessageLimit(),
                entity.getHistoryWindow(),
                entity.getMaxMessageCharacters(),
                activeChatModel);
    }
}
