package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 导入文章编辑请求（C08）：修改标题/正文文本/封面；图片块保留。 */
@Schema(description = "官方通知草稿编辑请求")
public record AdminOfficialFeedArticleUpdateRequest(
        @Schema(description = "标题") @NotBlank @Size(max = 255) String title,
        @Schema(description = "正文纯文本（覆盖文本块，图片块保留）") @Size(max = 20000) String plainText,
        @Schema(description = "封面地址（可空，空则沿用自动提取的首图）") @Size(max = 512) String coverUrl) {
}
