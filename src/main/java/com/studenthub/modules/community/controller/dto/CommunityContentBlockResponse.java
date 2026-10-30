package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文章内容块响应：IMAGE 块附带签名展示地址与缩略图地址。
 */
@Schema(description = "文章内容块（响应）")
public record CommunityContentBlockResponse(
        @Schema(description = "块类型：TEXT / IMAGE") String type,
        @Schema(description = "文本内容（IMAGE 块为 null）") String text,
        @Schema(description = "图片文件 ID（TEXT 块为 null）") Long fileId,
        @Schema(description = "图片签名访问地址（IMAGE 块）") String url,
        @Schema(description = "图片缩略图签名地址（IMAGE 块）") String thumbnailUrl) {
}
