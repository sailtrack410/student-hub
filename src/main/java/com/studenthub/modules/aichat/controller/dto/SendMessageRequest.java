package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "发送 AI 消息请求")
public record SendMessageRequest(
        @Schema(description = "消息文本，携带图片时可空", nullable = true)
        @Size(max = 2000) String content,
        @Schema(description = "图片文件 ID 列表（先经 /api/app/files 上传），最多 4 张", nullable = true)
        List<Long> images) {

    public static final int MAX_IMAGES = 4;
}