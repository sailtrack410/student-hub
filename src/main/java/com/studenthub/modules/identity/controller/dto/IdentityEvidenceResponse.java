package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "身份审核材料（文件不存在时 previewAvailable=false，页面显示「文件缺失」而非占位图）")
public record IdentityEvidenceResponse(
        Long id,
        Long fileId,
        String materialType,
        String materialName,
        String remark,
        String originalName,
        String contentType,
        Long fileSize,
        @Schema(description = "是否图片（决定前端用 img 还是文档卡片）") boolean image,
        @Schema(description = "短时签名访问路径；文件缺失时为 null") String viewPath,
        @Schema(description = "图片缩略图签名路径；非图片或缺失时为 null") String thumbnailPath,
        @Schema(description = "文件是否仍可访问") boolean previewAvailable,
        Long uploadedBy,
        LocalDateTime uploadedAt) {
}
