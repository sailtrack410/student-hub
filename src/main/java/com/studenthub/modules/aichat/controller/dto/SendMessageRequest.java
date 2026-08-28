package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "发送 AI 消息请求")
public record SendMessageRequest(
        @Schema(description = "消息内容") @NotBlank @Size(max = 2000) String content) {
}
