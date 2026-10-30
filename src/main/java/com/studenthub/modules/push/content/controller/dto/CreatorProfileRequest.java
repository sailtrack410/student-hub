package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "创作者档案创建/更新（等级、昵称、备注；认证走 certify 接口）")
public record CreatorProfileRequest(
        @Schema(description = "用户 ID（创建时必填；更新时忽略）") Long userId,
        @Schema(description = "展示昵称，留空取用户昵称") @Size(max = 100) String displayName,
        @Schema(description = "创作者等级 1-5") @Min(1) @Max(5) Integer level,
        @Schema(description = "创作者积分") @Min(0) @Max(1000000) Integer score,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
