package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "功能开关")
public record AdminFeatureFlagResponse(
        @Schema(description = "配置键") String key,
        @Schema(description = "名称") String name,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "灰度比例 0-100；100 表示全量") int rolloutPercent,
        @Schema(description = "说明") String description) {
}
