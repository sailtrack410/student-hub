package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 官方号运营：指定官方账号对帖子发官方回复（C12）。 */
@Schema(description = "官方账号回复帖子请求")
public record AdminOfficialReplyRequest(
        @Schema(description = "官方账号用户 ID（必须是官方类账号且与帖子同校区）") @NotNull Long officialUserId,
        @Schema(description = "回复内容（纯文本，≤500 字）") @NotBlank @Size(max = 500) String content) {
}
