package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 公开大屏 · AI 问答请求。question 为当前提问，history 为最近几轮上下文（上限 6 条）。
 */
@Schema(description = "公开 AI 问答请求")
public record PublicChatRequest(
        @NotBlank @Size(max = 500) String question,
        @Size(max = 6) List<Turn> history) {

    public record Turn(String role, String content) {
    }
}