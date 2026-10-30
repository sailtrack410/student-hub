package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 回答引用的知识库来源 */
@Schema(description = "知识库引用来源")
public record AiSourceResponse(
        @Schema(description = "文件名") String fileName,
        @Schema(description = "页码，DOCX/MD 为 null", nullable = true) Integer page,
        @Schema(description = "章节路径", nullable = true) String titlePath,
        @Schema(description = "融合分 0~1") double score,
        @Schema(description = "引用片段（截断）", nullable = true) String snippet) {
}
