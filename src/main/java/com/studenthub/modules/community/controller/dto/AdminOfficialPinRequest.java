package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 官方号运营：帖子置顶/取消置顶（C12）。 */
@Schema(description = "帖子置顶请求")
public record AdminOfficialPinRequest(
        @Schema(description = "true 置顶 / false 取消置顶") @NotNull Boolean pinned) {
}
