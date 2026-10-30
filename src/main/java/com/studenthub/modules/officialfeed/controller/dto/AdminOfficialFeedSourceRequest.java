package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "官方通知抓取来源新建/更新")
public record AdminOfficialFeedSourceRequest(
        @Schema(description = "来源名称") @NotBlank @Size(max = 64) String sourceName,
        @Schema(description = "栏目列表页地址（仅允许 *.mycc.edu.cn）") @NotBlank @Size(max = 512) String listUrl,
        @Schema(description = "发布到哪个社区分区") @NotNull Long sectionId,
        @Schema(description = "以哪个官方账号发布") @NotNull Long authorId,
        @Schema(description = "列表最多翻页数（1-5）") @Min(1) @Max(5) Integer maxPages,
        @Schema(description = "单次最多发布篇数（1-10）") @Min(1) @Max(10) Integer maxArticles,
        @Schema(description = "超过该天数的旧通知只记录不发布") @Min(1) @Max(3650) Integer maxAgeDays,
        @Schema(description = "是否启用定时抓取") Boolean enabled) {
}
