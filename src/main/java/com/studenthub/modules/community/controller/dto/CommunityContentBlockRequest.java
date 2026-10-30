package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 文章图文混排块：type=TEXT 时 text 必填；type=IMAGE 时 fileId 必填。
 * 图片按块顺序即正文中的展示顺序，总数最多 9 张。
 */
@Schema(description = "文章内容块")
public record CommunityContentBlockRequest(
        @Schema(description = "块类型：TEXT / IMAGE") @Size(max = 16) String type,
        @Schema(description = "文本内容（TEXT 必填，单块 ≤5000 字）") @Size(max = 5000) String text,
        @Schema(description = "图片文件 ID（IMAGE 必填，需为自己上传）") Long fileId) {
}
