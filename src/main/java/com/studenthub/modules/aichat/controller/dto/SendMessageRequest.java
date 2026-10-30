package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发送 AI 消息请求（2026-10-17 与前端需求清单 6.4 对齐）：
 * attachments 多模态附件（IMAGE/DOCUMENT）为主；旧字段 images 保留兼容，
 * 两者同时传时以 attachments 为准。DOCUMENT 内容由服务端读取后注入提示词。
 */
@Schema(description = "发送 AI 消息请求")
public record SendMessageRequest(
        @Schema(description = "消息文本，携带附件时可空", nullable = true)
        @Size(max = 2000) String content,
        @Schema(description = "[兼容旧版] 图片文件 ID 列表；与 attachments 同时传时以 attachments 为准", nullable = true, deprecated = true)
        List<Long> images,
        @Schema(description = "多模态附件列表（IMAGE/DOCUMENT），fileId 先经 /api/app/files 上传；IMAGE 最多 4 张，DOCUMENT 最多 2 份", nullable = true)
        List<Attachment> attachments,
        @Schema(description = "本次提问唯一标识；流式回退与重试复用相同值，可空兼容旧客户端")
        @Size(max = 96) String clientRequestId) {

    public SendMessageRequest(String content, List<Long> images, List<Attachment> attachments) {
        this(content, images, attachments, null);
    }

    public static final int MAX_IMAGES = 4;
    public static final int MAX_DOCUMENTS = 2;

    public record Attachment(
            @Schema(description = "附件类型：IMAGE/DOCUMENT") String type,
            @Schema(description = "已上传文件 ID") Long fileId) {

        public boolean isDocument() {
            return "DOCUMENT".equalsIgnoreCase(type);
        }
    }

    /** 从 attachments 提取全部 IMAGE（无 attachments 时回退旧 images 字段） */
    public List<Long> effectiveImageIds() {
        if (attachments == null || attachments.isEmpty()) {
            return images == null ? List.of() : images;
        }
        return attachments.stream()
                .filter(a -> a != null && !a.isDocument() && a.fileId() != null)
                .map(Attachment::fileId)
                .toList();
    }

    /** 从 attachments 提取全部 DOCUMENT（旧 images 字段不参与文档语义） */
    public List<Long> effectiveDocumentIds() {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .filter(a -> a != null && a.isDocument() && a.fileId() != null)
                .map(Attachment::fileId)
                .toList();
    }
}
