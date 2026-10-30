package com.studenthub.modules.systemconfig.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "配置批量更新请求")
public record AdminConfigUpdateRequest(
        @NotNull List<@Valid Item> items) {

    @Schema(description = "单个配置更新项")
    public record Item(
            @Schema(description = "配置键") @NotNull String key,
            @Schema(description = "配置值") String value,
            @Schema(description = "作用域校区 ID，0 为平台级") Long campusId) {
    }
}
