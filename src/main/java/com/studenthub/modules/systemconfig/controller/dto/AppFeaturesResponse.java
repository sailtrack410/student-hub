package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "App 有效功能开关")
public record AppFeaturesResponse(
        @Schema(description = "生效校区；0 表示仅平台默认") Long campusId,
        @Schema(description = "功能开关键值") Map<String, Boolean> flags,
        @Schema(description = "生成时间") LocalDateTime generatedAt) {
}
