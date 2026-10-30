package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 帖子批量下架/恢复请求（C02）。单次最多 200 条，避免一次事务过大。 */
@Schema(description = "帖子批量处置请求：HIDE 下架 / RESTORE 恢复")
public record AdminCommunityBatchPostStatusRequest(
        @Schema(description = "帖子 ID 列表，最多 200 条") @NotEmpty @Size(max = 200) List<Long> postIds,
        @Schema(description = "动作：HIDE 下架 / RESTORE 恢复") @NotBlank String action,
        @Schema(description = "处置原因，会随站内通知告知作者") @Size(max = 500) String reason) {
}
