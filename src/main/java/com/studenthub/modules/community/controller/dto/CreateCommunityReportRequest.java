package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "提交社区举报")
public record CreateCommunityReportRequest(
        @Schema(description = "举报对象类型：POST 帖子 / COMMENT 评论 / MESSAGE 私信 / USER 用户") @NotBlank String targetType,
        @Schema(description = "举报对象 ID") @NotNull Long targetId,
        @Schema(description = "举报原因：AD 广告营销 / ABUSE 辱骂攻击 / PORN 色情低俗 / POLITICS 违法违规 / PRIVACY 泄露隐私 / OTHER 其他") @NotBlank String reasonCode,
        @Schema(description = "补充说明，便于治理端判断", nullable = true) @Size(max = 500) String reasonDetail) {
}
