package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "官方直投广告上下架")
public record AdminDirectAdStatusRequest(
        @Schema(description = "目标状态：ENABLED（上架）/DISABLED（下架）/DRAFT（草稿）")
        @NotBlank @Size(max = 16) String status) {
}
