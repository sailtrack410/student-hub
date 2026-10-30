package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端系统健康")
public record AdminSystemHealthResponse(
        @Schema(description = "总体状态 UP/DEGRADED/DOWN") String status,
        @Schema(description = "检查时间") LocalDateTime checkedAt,
        @Schema(description = "组件状态") List<Component> components) {

    @Schema(description = "健康组件")
    public record Component(
            @Schema(description = "名称") String name,
            @Schema(description = "状态 UP/DOWN/NOT_CONFIGURED") String status,
            @Schema(description = "详情") String detail,
            @Schema(description = "耗时毫秒") Long latencyMs) {
    }
}
