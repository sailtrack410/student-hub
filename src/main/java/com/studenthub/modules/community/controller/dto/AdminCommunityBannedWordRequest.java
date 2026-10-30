package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端屏蔽词新建/更新请求（V71 支持正则与命中统计） */
@Schema(description = "屏蔽词新建/更新请求")
public record AdminCommunityBannedWordRequest(
        @Schema(description = "屏蔽词（matchType=REGEX 时为正则表达式）") @NotBlank @Size(max = 200) String word,
        @Schema(description = "备注") @Size(max = 255) String remark,
        @Schema(description = "状态：ENABLED/DISABLED") String status,
        @Schema(description = "匹配方式：LITERAL 字面量（默认）/ REGEX 正则") String matchType) {
}
