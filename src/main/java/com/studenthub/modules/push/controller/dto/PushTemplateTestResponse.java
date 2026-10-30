package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "模板测试发送结果：站内通知是否落库 + 各渠道真实投递状态")
public record PushTemplateTestResponse(
        @Schema(description = "站内通知是否已写入当前管理员的通知箱") boolean notificationCreated,
        @Schema(description = "渲染后的标题") String title,
        @Schema(description = "渲染后的正文") String content,
        @Schema(description = "实际尝试的渠道") List<String> attemptedChannels,
        @Schema(description = "真正具备投递能力并已尝试的渠道") List<String> configuredChannels,
        @Schema(description = "未配置/未实现的渠道及原因") List<ChannelOutcome> notConfigured,
        @Schema(description = "各渠道投递结果") List<ChannelOutcome> results) {

    @Schema(description = "单渠道结果")
    public record ChannelOutcome(String channel, String status, String message) {
    }
}
