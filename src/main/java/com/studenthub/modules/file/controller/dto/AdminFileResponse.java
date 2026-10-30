package com.studenthub.modules.file.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端文件")
public record AdminFileResponse(
        @Schema(description = "文件 ID") Long fileId,
        @Schema(description = "原始文件名") String originalName,
        @Schema(description = "内容类型") String contentType,
        @Schema(description = "文件字节数") Long fileSize,
        @Schema(description = "上传人 ID") Long uploaderId,
        @Schema(description = "上传时间") LocalDateTime createdAt,
        @Schema(description = "短时签名预览地址") String viewPath) {
}
