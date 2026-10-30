package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 单篇 URL 导入请求（C08）：只允许学校官网域名（*.mycc.edu.cn），先落草稿再预览/编辑/发布。 */
@Schema(description = "官方通知单篇 URL 导入请求")
public record AdminOfficialFeedImportRequest(
        @Schema(description = "文章 URL（必须为学校官网域名）") @NotBlank @Size(max = 512) String url,
        @Schema(description = "归属抓取来源 ID（决定发布分区与主体；可空，默认第一个启用来源）") Long sourceId,
        @Schema(description = "是否已确认转载版权（确认后才能发布）") Boolean copyrightConfirmed) {
}
